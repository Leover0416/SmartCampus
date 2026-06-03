package com.simon.campus.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddingService - DashScope 响应解析")
class EmbeddingServiceTest {

    @Test
    @DisplayName("按 text_index 还原批量 embedding 顺序")
    void parseDashScopeEmbeddingsByTextIndex() throws Exception {
        String body = """
            {
              "output": {
                "embeddings": [
                  { "text_index": 1, "embedding": [0.2, 0.3] },
                  { "text_index": 0, "embedding": [0.1, 0.4] }
                ]
              }
            }
            """;

        var embeddings = EmbeddingService.parseEmbeddingResponse(new ObjectMapper(), body, 2, 2);

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(0.1f, 0.4f);
        assertThat(embeddings.get(1)).containsExactly(0.2f, 0.3f);
    }

    @Test
    @DisplayName("批量 embedding 每次请求不超过 DashScope 上限")
    void batchSizeDoesNotExceedDashScopeLimit() {
        assertThat(EmbeddingService.BATCH_SIZE).isLessThanOrEqualTo(10);
    }
}
