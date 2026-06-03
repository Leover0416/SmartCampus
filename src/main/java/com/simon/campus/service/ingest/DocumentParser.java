package com.simon.campus.service.ingest;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DocumentParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(第[一二三四五六七八九十百千]+[章节条款]|\\d+\\.\\d*\\s|[一二三四五六七八九十]+[、.]|#+\\s).{2,50}$",
        Pattern.MULTILINE
    );

    private static final Pattern PAGE_BREAK = Pattern.compile("\\f|(?:---\\s*第\\s*\\d+\\s*页\\s*---)");

    public record ParsedSection(String heading, String content, int pageStart, int level) {}

    private final VisionTextExtractor visionTextExtractor;

    public DocumentParser() {
        this(VisionTextExtractor.noop());
    }

    @Autowired
    public DocumentParser(ObjectProvider<VisionTextExtractor> visionTextExtractorProvider) {
        this(visionTextExtractorProvider.getIfAvailable(VisionTextExtractor::noop));
    }

    DocumentParser(VisionTextExtractor visionTextExtractor) {
        this.visionTextExtractor = visionTextExtractor == null ? VisionTextExtractor.noop() : visionTextExtractor;
    }

    public List<ParsedSection> parse(InputStream inputStream, String contentType) throws Exception {
        return parse(inputStream, contentType, false);
    }

    public List<ParsedSection> parseForIngest(InputStream inputStream, String contentType) throws Exception {
        return parse(inputStream, contentType, true);
    }

    private List<ParsedSection> parse(InputStream inputStream, String contentType, boolean includeVisualPdfText) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        String fullText = extractText(bytes, contentType);
        if (includeVisualPdfText && isPdf(contentType)) {
            String visualText = extractVisualPdfText(bytes, fullText);
            if (!visualText.isBlank()) {
                fullText = mergeText(fullText, visualText);
            }
        }
        return splitIntoSections(fullText);
    }

    private String extractText(byte[] bytes, String contentType) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        if (contentType != null) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        ParseContext context = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();
        parser.parse(new ByteArrayInputStream(bytes), handler, metadata, context);
        return handler.toString();
    }

    private String extractVisualPdfText(byte[] pdfBytes, String tikaText) throws Exception {
        if (!visionTextExtractor.isAvailable()) {
            if (tikaText == null || tikaText.isBlank()) {
                log.warn("PDF 文本解析为空，且视觉解析未启用或未配置 DashScope API Key");
            }
            return "";
        }
        return visionTextExtractor.extractPdfImagesAsMarkdown(pdfBytes);
    }

    private String mergeText(String text, String visualText) {
        String cleanText = text == null ? "" : text.strip();
        String cleanVisual = visualText == null ? "" : visualText.strip();
        if (cleanText.isBlank()) return cleanVisual;
        if (cleanVisual.isBlank()) return cleanText;
        return cleanText + "\n\n# PDF 图片解析内容\n\n" + cleanVisual;
    }

    private boolean isPdf(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("pdf");
    }

    private List<ParsedSection> splitIntoSections(String text) {
        List<ParsedSection> sections = new ArrayList<>();
        String[] lines = text.split("\n");

        StringBuilder currentContent = new StringBuilder();
        String currentHeading = "引言";
        int currentPage = 1;
        int currentLevel = 0;

        for (String line : lines) {
            // Track page number via form feed characters
            long ffCount = line.chars().filter(c -> c == '\f').count();
            currentPage += (int) ffCount;
            String cleanLine = line.replace("\f", "").trim();

            if (cleanLine.isEmpty()) {
                currentContent.append("\n");
                continue;
            }

            if (isHeading(cleanLine)) {
                // Save current section if it has content
                String content = currentContent.toString().strip();
                if (!content.isEmpty()) {
                    sections.add(new ParsedSection(currentHeading, content, currentPage, currentLevel));
                }
                currentHeading = cleanLine;
                currentLevel = detectLevel(cleanLine);
                currentContent = new StringBuilder();
            } else {
                currentContent.append(cleanLine).append("\n");
            }
        }

        // Save last section
        String content = currentContent.toString().strip();
        if (!content.isEmpty()) {
            sections.add(new ParsedSection(currentHeading, content, currentPage, currentLevel));
        }

        // If no sections detected, return whole text as single section
        if (sections.isEmpty() && !text.isBlank()) {
            sections.add(new ParsedSection("全文", text.strip(), 1, 0));
        }

        return sections;
    }

    private boolean isHeading(String line) {
        if (line.length() > 80) return false;
        return HEADING_PATTERN.matcher(line).find();
    }

    private int detectLevel(String heading) {
        if (heading.startsWith("#")) {
            int level = 0;
            for (char c : heading.toCharArray()) {
                if (c == '#') level++;
                else break;
            }
            return level;
        }
        if (heading.matches("^第[一二三四五六七八九十百千]+章.*")) return 1;
        if (heading.matches("^第[一二三四五六七八九十百千]+节.*")) return 2;
        if (heading.matches("^\\d+\\.\\s.*")) return 2;
        if (heading.matches("^\\d+\\.\\d+.*")) return 3;
        if (heading.matches("^[一二三四五六七八九十]+[、.].*")) return 2;
        return 1;
    }
}
