package com.simon.campus.service.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DashScopeVisionTextExtractor implements VisionTextExtractor {

    private static final String IMAGE_PROMPT = """
        qwenvl markdown。
        请解析这页 PDF 图片中的全部可读内容，输出适合知识库检索的 Markdown。
        要求：
        1. 保留标题、段落、表格、列表、流程、图示中的文字和关键信息。
        2. 对非文字图片用简短中文描述说明其含义。
        3. 不要输出寒暄、不要编造图片中不存在的信息。
        """;

    private static final String CHAT_IMAGE_PROMPT = """
        请解析用户上传的图片内容，输出后续问答可直接引用的中文 Markdown。
        要求：
        1. 如果图片包含文字、表格、截图、票据、成绩单、通知公告，请尽量完整转写。
        2. 如果图片是场景或物品，请描述关键元素、位置关系和可能含义。
        3. 不要编造图片中不存在的信息；看不清的内容说明“无法辨认”。
        """;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    @Value("${knowledge.vision.pdf-image-enabled:true}")
    private boolean enabled;

    @Value("${knowledge.vision.pdf-render-dpi:144}")
    private int renderDpi;

    @Value("${knowledge.vision.pdf-max-pages:30}")
    private int maxPages;

    @Value("${models.vision-parser.model:qwen3-vl-flash}")
    private String model;

    @Value("${models.vision-parser.temperature:0.1}")
    private double temperature;

    @Value("${models.vision-parser.max-tokens:2048}")
    private int maxTokens;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank() && !apiKey.contains("your-dashscope-api-key");
    }

    @Override
    public String extractPdfImagesAsMarkdown(byte[] pdfBytes) throws Exception {
        if (!isAvailable()) return "";

        List<String> pageTexts = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), Math.max(1, maxPages));
            log.info("[INGEST_FLOW] step=pdf_visual_parse pages={} maxPages={} model={} dpi={}",
                document.getNumberOfPages(), pages, model, renderDpi);
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, renderDpi, ImageType.RGB);
                String markdown = parsePageImage(image, pageIndex + 1);
                if (!markdown.isBlank()) {
                    pageTexts.add("## 第 " + (pageIndex + 1) + " 页图片解析\n\n" + markdown.strip());
                }
            }
            if (document.getNumberOfPages() > pages) {
                pageTexts.add("> 视觉解析已达到最大页数限制，仅处理前 " + pages + " 页。");
            }
        }
        return String.join("\n\n", pageTexts);
    }

    @Override
    public String extractImageAsMarkdown(byte[] imageBytes, String mimeType) throws Exception {
        if (!isAvailable()) return "";
        String safeMimeType = (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType;
        long start = System.currentTimeMillis();
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        log.info("[MODEL_CALL] type=vision_chat_image model={} mimeType={} bytes={} prompt={}",
            model, safeMimeType, imageBytes.length, abbreviate(CHAT_IMAGE_PROMPT));
        String content = callVisionModel("data:" + safeMimeType + ";base64," + base64, CHAT_IMAGE_PROMPT, 120);
        log.info("[MODEL_RETURN] type=vision_chat_image model={} costMs={} response={}",
            model, System.currentTimeMillis() - start, abbreviate(content));
        return content;
    }

    private String parsePageImage(BufferedImage image, int pageNumber) throws Exception {
        long start = System.currentTimeMillis();
        String base64 = toJpegBase64(image);
        log.info("[MODEL_CALL] type=vision_pdf_page model={} page={} image={}x{} prompt={}",
            model, pageNumber, image.getWidth(), image.getHeight(), abbreviate(IMAGE_PROMPT));
        String content = callVisionModel("data:image/jpeg;base64," + base64, IMAGE_PROMPT, 180);
        log.info("[MODEL_RETURN] type=vision_pdf_page model={} page={} costMs={} response={}",
            model, pageNumber, System.currentTimeMillis() - start, abbreviate(content));
        return content;
    }

    private String callVisionModel(String imageUrl, String prompt, int timeoutSeconds) throws Exception {
        Map<String, Object> imagePart = Map.of(
            "type", "image_url",
            "image_url", Map.of("url", imageUrl)
        );
        Map<String, Object> textPart = Map.of(
            "type", "text",
            "text", prompt
        );

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(imagePart, textPart));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("enable_thinking", false);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("DashScope 视觉解析失败 [" + response.statusCode() + "]: " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText("");
    }

    private String toJpegBase64(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        }
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 3000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
