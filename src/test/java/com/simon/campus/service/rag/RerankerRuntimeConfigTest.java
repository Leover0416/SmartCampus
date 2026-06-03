package com.simon.campus.service.rag;

import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Reranker - 安全策略配置")
class RerankerRuntimeConfigTest {

    @Test
    @DisplayName("降级返回数量使用 system_config 中的 rerank topN")
    void fallbackUsesRuntimeTopNSetting() {
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getInt("rag.rerank.top_n", 12)).thenReturn(3);

        Reranker reranker = new Reranker(configService);

        List<RecallCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            candidates.add(RecallCandidate.builder()
                .childId("c" + i)
                .content("content " + i)
                .build());
        }

        assertThat(reranker.fallbackCandidates(candidates)).hasSize(3);
    }
}
