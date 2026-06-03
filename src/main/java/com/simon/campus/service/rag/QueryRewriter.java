package com.simon.campus.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.common.LlmClient;
import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG Stage 2：查询改写 / 查询扩展（Query Expansion）。
 * <p>
 * 用 LLM 将用户问题扩成：
 * <ul>
 *   <li>{@code mainQuery} — 完整核心问句</li>
 *   <li>{@code subQueries} — 2~3 个不同角度的子问题（供 Milvus 多次检索）</li>
 *   <li>{@code keywords} — 关键词列表（当前未接入召回）</li>
 * </ul>
 * 失败时降级：仅保留原问，子查询为空。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QueryRewriter {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final SystemConfigService configService;

    /** 要求模型只输出 JSON，便于 Jackson 反序列化为 {@link QueryExpansion} */
    private static final String SYSTEM_PROMPT = """
        你是一个查询改写专家，帮助改善知识库检索效果。
        给定用户问题，输出严格的JSON格式（不要有markdown代码块）：
        {
          "mainQuery": "改写后的完整核心问题",
          "subQueries": ["从不同角度的子问题1", "子问题2"],
          "keywords": ["关键词1", "关键词2", "关键词3"]
        }
        要求：
        - mainQuery 是最重要的独立完整问题
        - subQueries 2-3个，覆盖不同检索角度
        - keywords 3-6个核心词（名词为主）
        只输出JSON，不要有任何其他文字。
        """;

    /**
     * @param query 通常为 ContextMerger 融合后的问句
     * @return 扩展结果；LLM 异常时 mainQuery=原问、subQueries/keywords 为空列表
     */
    public QueryExpansion rewrite(String query) {
        try {
            String model = configService.get("models.query-rewriter.model", "qwen-plus");
            double temperature = configService.getDouble("models.query-rewriter.temperature", 0.2);
            int maxTokens = configService.getInt("models.query-rewriter.max-tokens", 512);

            // 阻塞调用 DashScope，输出 JSON 字符串
            String result = llmClient.chat(model, temperature, maxTokens,
                List.of(LlmClient.systemMsg(SYSTEM_PROMPT), LlmClient.userMsg(query)));

            // 部分模型会在 JSON 外包裹 ```json 代码块，需剥离
            String json = result.strip()
                .replaceAll("^```json\\s*", "").replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");

            QueryExpansion expansion = objectMapper.readValue(json, QueryExpansion.class);
            log.debug("QueryRewriter expanded '{}' → main='{}', subs={}",
                query, expansion.getMainQuery(), expansion.getSubQueries());
            return expansion;
        } catch (Exception e) {
            // 改写失败不阻断主流程：后续仍用原问做 BM25 / main Dense
            log.warn("QueryRewriter failed for '{}': {}. Using fallback.", query, e.getMessage());
            QueryExpansion fallback = new QueryExpansion();
            fallback.setMainQuery(query);
            fallback.setSubQueries(List.of());
            fallback.setKeywords(List.of());
            return fallback;
        }
    }
}
