package com.simon.campus.service.rag;

import com.simon.campus.common.LlmClient;
import com.simon.campus.session.SessionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG Stage 1：多轮上下文融合（指代消解）。
 * <p>
 * 与 Stage 2 的「子查询 subQueries」不同：本阶段把短句补全成<strong>一个</strong>独立问句，
 * 例如「那绩点要求是多少」→「计算机学院转专业的绩点要求是多少」。
 * <p>
 * 依赖 Redis 中 {@link SessionContext#history}；若 history 为空则直接返回原问。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextMerger {

    private final LlmClient llmClient;

    @Value("${models.query-rewriter.model}")
    private String model;

    private static final String SYSTEM_PROMPT = """
        你是一个上下文融合专家。给定对话历史和用户的最新问题，输出一个完整独立的问题。
        要求：
        1. 消除指代词（如"那个"、"它"、"他们"），替换为具体对象
        2. 补全省略的主语/宾语
        3. 如果问题已经完整独立，直接返回原问题
        4. 只输出问题本身，不要有任何解释
        """;

    /**
     * @param latestQuestion 用户本轮原始输入
     * @param session        Redis 会话，含最近若干轮 history
     * @return 融合后的单句，供后续 FAQ / 意图 / QueryRewriter 使用
     */
    public String merge(String latestQuestion, SessionContext session) {
        // 取最近 5 轮对话（user+assistant 各算一条）
        String history = session.buildHistoryText(5);
        if (history.isBlank()) {
            // 首轮对话无需调用 LLM
            return latestQuestion;
        }
        try {
            String userContent = "对话历史：\n" + history + "\n\n用户最新问题：" + latestQuestion;
            String result = llmClient.chat(model, 0.1, 256,
                List.of(LlmClient.systemMsg(SYSTEM_PROMPT), LlmClient.userMsg(userContent)));
            String merged = result.strip();
            log.debug("ContextMerger: '{}' → '{}'", latestQuestion, merged);
            return merged.isEmpty() ? latestQuestion : merged;
        } catch (Exception e) {
            log.warn("ContextMerger failed, using original: {}", e.getMessage());
            return latestQuestion;
        }
    }
}
