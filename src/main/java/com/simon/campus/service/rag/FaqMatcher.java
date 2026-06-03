package com.simon.campus.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.service.admin.SystemConfigService;
import com.simon.campus.service.ingest.EmbeddingService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * FAQ matcher using cached vector similarity.
 * Score ≥ 0.92 → short-circuit; 0.85–0.92 → add to RRF candidates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaqMatcher {

    private static final String CACHE_KEY = FaqEmbeddingService.CACHE_KEY;
    private final FaqPairMapper faqPairMapper;
    private final EmbeddingService embeddingService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigService configService;
    private final FaqEmbeddingService faqEmbeddingService;

    public record FaqMatchResult(
        boolean shortCircuited,
        String directAnswer,
        List<RecallCandidate> candidates
    ) {}

    /** Serializable FAQ entry for Redis caching. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqEntry {
        private Long id;
        private String question;
        private String answer;
        private float[] vector;
    }

    public FaqMatchResult match(String query) {
        try {
            float[] queryVec = embeddingService.embedOne(query);
            List<FaqEntry> entries = loadFaqEntries();
            if (entries.isEmpty()) return new FaqMatchResult(false, null, Collections.emptyList());

            double bestScore = -1;
            FaqEntry bestEntry = null;
            List<RecallCandidate> candidates = new ArrayList<>();
            double exactThreshold = configService.getDouble("faq.match.exact_thresh", 0.92);
            double candidateThreshold = configService.getDouble("faq.match.candidate_thresh", 0.85);

            for (FaqEntry e : entries) {
                double score = cosine(queryVec, e.getVector());
                if (score >= exactThreshold) {
                    if (score > bestScore) {
                        bestScore = score;
                        bestEntry = e;
                    }
                } else if (score >= candidateThreshold) {
                    candidates.add(RecallCandidate.builder()
                        .childId("faq_" + e.getId())
                        .parentId("faq_" + e.getId())
                        .docId("faq")
                        .docTitle("常见问题")
                        .headingPath(e.getQuestion())
                        .content(e.getQuestion() + "\n" + e.getAnswer())
                        .pageStart(null)
                        .score(score)
                        .source("faq")
                        .build());
                }
            }

            if (bestEntry != null) {
                incrementHitCount(bestEntry.getId());
                return new FaqMatchResult(true, bestEntry.getAnswer(), Collections.emptyList());
            }
            return new FaqMatchResult(false, null, candidates);
        } catch (Exception e) {
            log.warn("FaqMatcher failed: {}", e.getMessage());
            return new FaqMatchResult(false, null, Collections.emptyList());
        }
    }

    private List<FaqEntry> loadFaqEntries() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            try {
                FaqEntry[] arr = objectMapper.readValue(cached, FaqEntry[].class);
                return Arrays.asList(arr);
            } catch (Exception e) {
                log.warn("FAQ cache parse failed, reloading from DB");
            }
        }
        return rebuildCache();
    }

    public List<FaqEntry> rebuildCache() {
        faqEmbeddingService.syncAllMissing();
        List<FaqPair> pairs = faqPairMapper.findAllEnabled();
        List<FaqEntry> entries = new ArrayList<>();
        for (FaqPair p : pairs) {
            if (p.getEmbeddingJson() == null || p.getEmbeddingJson().isBlank()) continue;
            try {
                float[] vec = objectMapper.readValue(p.getEmbeddingJson(), float[].class);
                entries.add(new FaqEntry(p.getId(), p.getQuestion(), p.getAnswer(), vec));
            } catch (Exception e) {
                log.debug("Skip FAQ {} - bad embedding: {}", p.getId(), e.getMessage());
            }
        }
        try {
            redisTemplate.opsForValue().set(CACHE_KEY,
                objectMapper.writeValueAsString(entries), Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to cache FAQ entries: {}", e.getMessage());
        }
        return entries;
    }

    /** Preload Redis FAQ vector cache (called on startup). */
    public void warmCache() {
        rebuildCache();
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void incrementHitCount(Long faqId) {
        try {
            faqPairMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FaqPair>()
                    .eq(FaqPair::getId, faqId)
                    .setSql("hit_count = hit_count + 1"));
        } catch (Exception e) {
            log.debug("Failed to increment FAQ hit_count: {}", e.getMessage());
        }
    }
}
