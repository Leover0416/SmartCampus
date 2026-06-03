package com.simon.campus.service.rag;

import com.simon.campus.common.LlmClient;
import com.simon.campus.service.admin.SystemConfigService;
import com.simon.campus.session.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagGenerator - 运行时配置")
class RagGeneratorRuntimeConfigTest {

    @Test
    @DisplayName("闲聊生成使用数据库中的模型、Prompt、温度和Token参数")
    void chitchatUsesRuntimeSettings() throws Exception {
        LlmClient llmClient = mock(LlmClient.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.get("models.chitchat.model", "qwen-turbo")).thenReturn("qwen-max-latest");
        when(configService.getDouble("models.chitchat.temperature", 0.7)).thenReturn(0.6);
        when(configService.getInt("models.chitchat.max-tokens", 1024)).thenReturn(768);
        when(configService.get("prompt.chitchat_default", RagGenerator.DEFAULT_CHITCHAT_SYSTEM))
            .thenReturn("自定义闲聊提示词");
        when(llmClient.chatStream(eq("qwen-max-latest"), eq(0.6), eq(768), any(), any()))
            .thenReturn("你好");

        RagGenerator generator = new RagGenerator(llmClient, configService);

        String answer = generator.chitchatStream("你好", SessionContext.builder().build(), token -> {});

        assertThat(answer).isEqualTo("你好");
        verify(llmClient).chatStream(eq("qwen-max-latest"), eq(0.6), eq(768), any(List.class), any(Consumer.class));
    }
}
