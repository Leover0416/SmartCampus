package com.simon.campus.service.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
@Slf4j
public class EmbeddingService {

    private static final String EMBED_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";
    static final int BATCH_SIZE = 10;
    private static final int DIMENSION  = 1024;

    @Value("${dashscope.api-key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量向量化，每批最多 BATCH_SIZE 条，返回与输入顺序一致的 float[] 列表
     */
    public List<float[]> embedBatch(List<String> texts) throws Exception {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            result.addAll(embedSingleBatch(batch));
        }
        return result;
    }

    public float[] embedOne(String text) throws Exception {
        List<float[]> res = embedSingleBatch(List.of(text));
        return res.get(0);
    }

    private List<float[]> embedSingleBatch(List<String> texts) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[MODEL_CALL] type=embedding model=text-embedding-v3 count={} texts={}",
            texts.size(), abbreviate(String.valueOf(texts)));
        Map<String, Object> body = Map.of(
            "model", "text-embedding-v3",
            "input", Map.of("texts", texts),
            "parameters", Map.of("dimension", DIMENSION)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(EMBED_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(60))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("DashScope Embedding 调用失败 [" + response.statusCode() + "]: " + response.body());
        }

        List<float[]> embeddings = parseEmbeddingResponse(objectMapper, response.body(), texts.size(), DIMENSION);
        log.info("[MODEL_RETURN] type=embedding model=text-embedding-v3 costMs={} count={} dimension={} response={}",
            System.currentTimeMillis() - start, embeddings.size(), DIMENSION, abbreviate(response.body()));
        return embeddings;
    }

    static List<float[]> parseEmbeddingResponse(ObjectMapper objectMapper, String responseBody,
                                                int expectedCount, int dimension) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddings = root.path("output").path("embeddings");
        if (!embeddings.isArray()) {
            throw new RuntimeException("DashScope Embedding 响应缺少 output.embeddings");
        }

        // DashScope text-embedding-v3 returns text_index, not index.
        List<float[]> sorted = new ArrayList<>(Collections.nCopies(expectedCount, null));
        for (JsonNode node : embeddings) {
            int idx = node.has("text_index") ? node.path("text_index").asInt() : node.path("index").asInt();
            if (idx < 0 || idx >= expectedCount) {
                throw new RuntimeException("DashScope Embedding 响应 text_index 越界: " + idx);
            }
            JsonNode arr = node.path("embedding");
            if (!arr.isArray() || arr.size() != dimension) {
                throw new RuntimeException("DashScope Embedding 维度异常: expected=" + dimension + ", actual=" + arr.size());
            }
            float[] vec = new float[dimension];
            for (int i = 0; i < dimension; i++) {
                vec[i] = (float) arr.get(i).asDouble();
            }
            sorted.set(idx, vec);
        }

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) == null) {
                throw new RuntimeException("DashScope Embedding 响应缺少 text_index=" + i + " 的向量");
            }
        }
        return sorted;
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 3000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
