package com.simon.campus.service.agent;

import com.simon.campus.common.LlmClient;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntentRouter — 意图路由单元测试")
class IntentRouterTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private SystemConfigService configService;

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        lenient().when(configService.get("models.intent-router.model", "qwen-turbo")).thenReturn("qwen-turbo");
        lenient().when(configService.getDouble("models.intent-router.temperature", 0.1)).thenReturn(0.1);
        lenient().when(configService.getInt("models.intent-router.max-tokens", 16)).thenReturn(16);
        router = new IntentRouter(llmClient, configService);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → HUMAN (关键词快路由)")
    @ValueSource(strings = {"我要转人工", "找人工客服", "真人服务", "我要投诉这件事", "举报", "紧急求助"})
    @DisplayName("HUMAN 关键词快路由，不调用 LLM")
    void humanKeywords_fastPath_noLLMCall(String query) {
        assertThat(router.route(query)).isEqualTo("HUMAN");
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → ACADEMIC_TOOL (关键词快路由)")
    @ValueSource(strings = {"本学期校历", "什么时候选课", "考试安排是什么", "教务处联系方式", "教务处电话", "教务处邮箱", "什么时候开学"})
    @DisplayName("ACADEMIC_TOOL 关键词快路由，不调用 LLM")
    void academicKeywords_fastPath_noLLMCall(String query) {
        assertThat(router.route(query)).isEqualTo("ACADEMIC_TOOL");
        verifyNoInteractions(llmClient);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" → CHITCHAT (关键词快路由，短句)")
    @ValueSource(strings = {"你好", "hi", "hello", "谢谢", "再见", "你是谁"})
    @DisplayName("CHITCHAT 关键词快路由（短句 <20字），不调用 LLM")
    void chitchatKeywords_shortQuery_fastPath(String query) {
        assertThat(router.route(query)).isEqualTo("CHITCHAT");
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("无关键词命中 → 调用 LLM 分类，返回 LLM 结果")
    void noKeywordMatch_callsLLM() throws Exception {
        when(llmClient.chat(anyString(), anyDouble(), anyInt(), anyList()))
            .thenReturn("POLICY_QA");

        String result = router.route("挂科了对绩点有什么影响");

        assertThat(result).isEqualTo("POLICY_QA");
        verify(llmClient, times(1)).chat(anyString(), anyDouble(), anyInt(), anyList());
    }

    @Test
    @DisplayName("LLM 返回无效标签 → 降级为 POLICY_QA")
    void llmReturnsInvalidIntent_fallsBackToPolicyQA() throws Exception {
        when(llmClient.chat(anyString(), anyDouble(), anyInt(), anyList()))
            .thenReturn("UNKNOWN_INTENT");

        String result = router.route("帮我查一下奖学金评定结果");

        assertThat(result).isEqualTo("POLICY_QA");
    }

    @Test
    @DisplayName("LLM 调用抛异常 → 降级为 POLICY_QA")
    void llmThrowsException_fallsBackToPolicyQA() throws Exception {
        when(llmClient.chat(anyString(), anyDouble(), anyInt(), anyList()))
            .thenThrow(new RuntimeException("Network error"));

        String result = router.route("转专业需要满足什么条件");

        assertThat(result).isEqualTo("POLICY_QA");
    }
}
