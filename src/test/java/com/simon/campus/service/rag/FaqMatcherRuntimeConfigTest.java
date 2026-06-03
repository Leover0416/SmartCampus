package com.simon.campus.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.service.admin.SystemConfigService;
import com.simon.campus.service.ingest.EmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FaqMatcher - 安全策略配置")
class FaqMatcherRuntimeConfigTest {

    @Test
    @DisplayName("FAQ 精确命中阈值从 system_config 读取")
    void matchUsesRuntimeExactThreshold() throws Exception {
        FaqPairMapper faqPairMapper = mock(FaqPairMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("faq:vectors")).thenReturn(null);
        when(embeddingService.embedOne("绩点怎么算")).thenReturn(new float[]{1, 0});
        when(configService.getDouble("faq.match.exact_thresh", 0.92)).thenReturn(0.8);
        when(configService.getDouble("faq.match.candidate_thresh", 0.85)).thenReturn(0.7);

        FaqPair pair = new FaqPair();
        pair.setId(1L);
        pair.setQuestion("绩点计算");
        pair.setAnswer("按课程绩点加权平均。");
        pair.setEmbeddingJson(objectMapper.writeValueAsString(new float[]{0.9f, 0.1f}));
        when(faqPairMapper.findAllEnabled()).thenReturn(List.of(pair));
        FaqEmbeddingService faqEmbeddingService = mock(FaqEmbeddingService.class);

        FaqMatcher matcher = new FaqMatcher(
            faqPairMapper, embeddingService, redisTemplate, objectMapper, configService, faqEmbeddingService);

        FaqMatcher.FaqMatchResult result = matcher.match("绩点怎么算");

        assertThat(result.shortCircuited()).isTrue();
        assertThat(result.directAnswer()).isEqualTo("按课程绩点加权平均。");
    }
}
