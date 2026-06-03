package com.simon.campus.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.service.ingest.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates and persists FAQ question embeddings; invalidates Redis vector cache.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaqEmbeddingService {

    static final String CACHE_KEY = "faq:vectors";

    private final FaqPairMapper faqPairMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public void syncEmbedding(Long id, String question) throws Exception {
        float[] vec = embeddingService.embedOne(question);
        String json = objectMapper.writeValueAsString(vec);
        faqPairMapper.update(null, new LambdaUpdateWrapper<FaqPair>()
            .eq(FaqPair::getId, id)
            .set(FaqPair::getEmbeddingJson, json)
            .set(FaqPair::getUpdatedAt, LocalDateTime.now()));
    }

    /** Embed all enabled FAQs missing vectors; returns count synced. */
    public int syncAllMissing() {
        List<FaqPair> missing = faqPairMapper.selectList(new LambdaQueryWrapper<FaqPair>()
            .eq(FaqPair::getEnabled, 1)
            .and(w -> w.isNull(FaqPair::getEmbeddingJson)
                .or().eq(FaqPair::getEmbeddingJson, "")));
        int synced = 0;
        for (FaqPair pair : missing) {
            try {
                syncEmbedding(pair.getId(), pair.getQuestion());
                synced++;
            } catch (Exception e) {
                log.warn("Failed to embed FAQ id={} question='{}': {}",
                    pair.getId(), pair.getQuestion(), e.getMessage());
            }
        }
        if (synced > 0) {
            invalidateCache();
            log.info("Synced {} FAQ embedding(s)", synced);
        }
        return synced;
    }

    public void invalidateCache() {
        redisTemplate.delete(CACHE_KEY);
    }
}
