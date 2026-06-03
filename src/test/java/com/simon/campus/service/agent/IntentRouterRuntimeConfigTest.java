package com.simon.campus.service.agent;

import com.simon.campus.common.LlmClient;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IntentRouter - 运行时配置")
class IntentRouterRuntimeConfigTest {

    @Test
    @DisplayName("LLM分类使用数据库中的模型、温度和Token参数")
    void routeUsesRuntimeModelSettings() throws Exception {
        LlmClient llmClient = mock(LlmClient.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.get("models.intent-router.model", "qwen-turbo")).thenReturn("qwen-plus");
        when(configService.getDouble("models.intent-router.temperature", 0.1)).thenReturn(0.4);
        when(configService.getInt("models.intent-router.max-tokens", 16)).thenReturn(64);
        when(llmClient.chat(eq("qwen-plus"), eq(0.4), eq(64), any())).thenReturn("POLICY_QA");

        IntentRouter router = new IntentRouter(llmClient, configService);

        String intent = router.route("转专业政策是什么");

        assertThat(intent).isEqualTo("POLICY_QA");
        verify(llmClient).chat(eq("qwen-plus"), eq(0.4), eq(64), any(List.class));
    }
}
