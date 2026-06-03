package com.simon.campus.service.rag;

import com.simon.campus.common.LlmClient;
import com.simon.campus.service.admin.SystemConfigService;
import com.simon.campus.session.SessionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stage 6: Generate streaming answer grounded in the assembled parent context.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagGenerator {

    private final LlmClient llmClient;
    private final SystemConfigService configService;

    public static final String DEFAULT_RAG_SYSTEM = """
        你是SmartCampus校园智能助手，专门回答学校政策、教务规定和校园服务相关问题。
        请根据提供的参考资料回答用户问题。要求：
        1. 答案必须基于参考资料中的内容，不要凭空编造
        2. 在答案中标注来源，格式：[来源: 文档名, 第X页]
        3. 如果参考资料中没有相关信息，回复"抱歉，知识库中暂无关于此问题的相关信息，建议您联系相关部门咨询。"
        4. 语气专业但友好，回答简洁有条理
        5. 如有多条规定，使用序号或要点格式
        """;

    public static final String DEFAULT_CHITCHAT_SYSTEM = """
        你是SmartCampus校园智能助手，友好、专业。
        如果是日常闲聊，轻松回应；如有校园相关问题，引导用户使用知识库查询功能。
        """;

    private static final String NO_CONTEXT_REPLY =
        "抱歉，知识库中暂无关于此问题的相关信息，建议您联系相关部门咨询。如需转接人工客服，请告知我。";

    /**
     * Streaming RAG generation. Calls onToken for each token, returns full content.
     */
    public String generateStream(String query, String context, SessionContext session,
                                  Consumer<String> onToken) throws Exception {
        if (context == null || context.isBlank()) {
            onToken.accept(NO_CONTEXT_REPLY);
            return NO_CONTEXT_REPLY;
        }

        String userContent = buildUserContent(query, context, session);
        String model = configService.get("models.rag-generator.model", "qwen-max");
        double temperature = configService.getDouble("models.rag-generator.temperature", 0.2);
        int maxTokens = configService.getInt("models.rag-generator.max-tokens", 2048);
        String systemPrompt = configService.get("prompt.rag_default", DEFAULT_RAG_SYSTEM);
        return llmClient.chatStream(model, temperature, maxTokens,
            LlmClient.toMaps(List.of(LlmClient.Msg.system(systemPrompt), LlmClient.Msg.user(userContent))),
            onToken);
    }

    /**
     * Streaming chitchat. No RAG context.
     */
    public String chitchatStream(String query, SessionContext session,
                                  Consumer<String> onToken) throws Exception {
        String history = session.buildHistoryText(5);
        String userContent = history.isBlank() ? query : history + "\n用户: " + query;
        String model = configService.get("models.chitchat.model", "qwen-turbo");
        double temperature = configService.getDouble("models.chitchat.temperature", 0.7);
        int maxTokens = configService.getInt("models.chitchat.max-tokens", 1024);
        String systemPrompt = configService.get("prompt.chitchat_default", DEFAULT_CHITCHAT_SYSTEM);
        return llmClient.chatStream(model, temperature, maxTokens,
            LlmClient.toMaps(List.of(LlmClient.Msg.system(systemPrompt), LlmClient.Msg.user(userContent))),
            onToken);
    }

    private String buildUserContent(String query, String context, SessionContext session) {
        String history = session.buildHistoryText(3);
        StringBuilder sb = new StringBuilder();
        if (!history.isBlank()) {
            sb.append("对话历史（供参考）：\n").append(history).append("\n\n");
        }
        sb.append("参考资料：\n").append(context).append("\n\n");
        sb.append("用户问题：").append(query);
        return sb.toString();
    }
}
