package com.simon.campus.service.agent;

import com.simon.campus.common.LlmClient;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Intent classifier using qwen-turbo.
 * Returns one of: POLICY_QA / DOC_SEARCH / ACADEMIC_TOOL / CHITCHAT / HUMAN
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntentRouter {

    private final LlmClient llmClient;
    private final SystemConfigService configService;

    private static final String SYSTEM = """
        你是一个意图分类器，将用户问题分类为以下5种之一：
        - POLICY_QA: 询问学校政策、规定、规则（如转专业、退学、成绩、考试等）
        - DOC_SEARCH: 通用文档搜索和信息查询
        - ACADEMIC_TOOL: 查询校历、选课时间、考试安排、部门联系方式等结构化信息
        - CHITCHAT: 闲聊、问候、与校园无关的对话
        - HUMAN: 明确要求转人工客服、有紧急投诉或情绪激动

        只输出一个标签，不要有任何解释。
        """;

    // Keyword fallback for fast path
    private static final List<String> ACADEMIC_KEYWORDS = List.of(
        "校历", "选课", "考试安排", "联系方式", "电话", "邮箱", "办公室", "开学", "放假", "学期");
    private static final List<String> HUMAN_KEYWORDS = List.of(
        "找老师", "转人工", "人工客服", "真人", "投诉", "举报", "紧急");
    private static final List<String> CHITCHAT_KEYWORDS = List.of(
        "你好", "hi", "hello", "谢谢", "再见", "你是谁", "介绍");

    public String route(String query) {
        String lower = query.toLowerCase();

        // Keyword fast path
        if (HUMAN_KEYWORDS.stream().anyMatch(lower::contains)) return "HUMAN";
        if (ACADEMIC_KEYWORDS.stream().anyMatch(lower::contains)) return "ACADEMIC_TOOL";
        if (CHITCHAT_KEYWORDS.stream().anyMatch(lower::contains) && query.length() < 20) return "CHITCHAT";

        try {
            String model = configService.get("models.intent-router.model", "qwen-turbo");
            double temperature = configService.getDouble("models.intent-router.temperature", 0.1);
            int maxTokens = configService.getInt("models.intent-router.max-tokens", 16);
            String result = llmClient.chat(model, temperature, maxTokens,
                List.of(LlmClient.systemMsg(SYSTEM), LlmClient.userMsg(query)));
            String intent = result.strip().toUpperCase();
            if (isValidIntent(intent)) {
                log.debug("Intent '{}' → {}", query, intent);
                return intent;
            }
            return "POLICY_QA";
        } catch (Exception e) {
            log.warn("IntentRouter failed: {}, defaulting to POLICY_QA", e.getMessage());
            return "POLICY_QA";
        }
    }

    private boolean isValidIntent(String s) {
        return switch (s) {
            case "POLICY_QA", "DOC_SEARCH", "ACADEMIC_TOOL", "CHITCHAT", "HUMAN" -> true;
            default -> false;
        };
    }
}
