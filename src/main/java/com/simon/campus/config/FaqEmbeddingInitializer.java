package com.simon.campus.config;

import com.simon.campus.service.rag.FaqEmbeddingService;
import com.simon.campus.service.rag.FaqMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On startup: backfill missing FAQ embeddings and warm Redis vector cache.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FaqEmbeddingInitializer implements ApplicationRunner {

    private final FaqEmbeddingService faqEmbeddingService;
    private final FaqMatcher faqMatcher;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int synced = faqEmbeddingService.syncAllMissing();
            faqMatcher.warmCache();
            log.info("FAQ vector cache ready (newly embedded: {})", synced);
        } catch (Exception e) {
            log.warn("FAQ embedding warmup skipped: {}", e.getMessage());
        }
    }
}
