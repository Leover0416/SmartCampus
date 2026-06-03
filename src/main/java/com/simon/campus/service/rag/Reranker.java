package com.simon.campus.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Stage 4: DashScope gte-rerank reranking.
 * Filters candidates with score < 0.3, keeps top 8–12.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class Reranker {

    private static final String RERANK_URL =
        "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
    private final SystemConfigService configService;

    @Value("${dashscope.api-key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RecallCandidate> rerank(String query, List<RecallCandidate> candidates) {
        if (candidates.isEmpty()) return candidates;
        try {
            long start = System.currentTimeMillis();
            List<String> docs = candidates.stream().map(RecallCandidate::getContent).toList();
            int topN = configService.getInt("rag.rerank.top_n", 12);
            double minScore = configService.getDouble("rag.rerank.score_thresh", 0.3);
            log.info("[MODEL_CALL] type=rerank model=gte-rerank query={} docs={}",
                abbreviate(query), abbreviate(String.valueOf(docs)));

            Map<String, Object> body = Map.of(
                "model", "gte-rerank",
                "input", Map.of("query", query, "documents", docs),
                "parameters", Map.of("top_n", Math.min(topN, docs.size()), "return_documents", false)
            );

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RERANK_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Rerank API returned {}: {}", response.statusCode(), response.body());
                return candidates;
            }

            JsonNode root = objectMapper.readTree(response.body());
            log.info("[MODEL_RETURN] type=rerank model=gte-rerank costMs={} response={}",
                System.currentTimeMillis() - start, abbreviate(response.body()));
            JsonNode results = root.path("output").path("results");

            List<RecallCandidate> reranked = new ArrayList<>();
            for (JsonNode r : results) {
                int idx = r.path("index").asInt();
                double score = r.path("relevance_score").asDouble();
                if (score >= minScore && idx < candidates.size()) {
                    RecallCandidate c = candidates.get(idx);
                    c.setScore(score);
                    reranked.add(c);
                }
            }
            log.debug("Reranker: {} in → {} out (threshold={})", candidates.size(), reranked.size(), minScore);
            return reranked.isEmpty() ? candidates.subList(0, Math.min(5, candidates.size())) : reranked;
        } catch (Exception e) {
            log.warn("Reranker failed, using original order: {}", e.getMessage());
            return fallbackCandidates(candidates);
        }
    }

    List<RecallCandidate> fallbackCandidates(List<RecallCandidate> candidates) {
        int topN = configService.getInt("rag.rerank.top_n", 12);
        return candidates.subList(0, Math.min(topN, candidates.size()));
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 3000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
