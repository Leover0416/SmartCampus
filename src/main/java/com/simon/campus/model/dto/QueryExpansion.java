package com.simon.campus.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Stage 2 查询改写结果（由 {@link com.simon.campus.service.rag.QueryRewriter} 产出）。
 * <p>
 * 下游用法：
 * <ul>
 *   <li>{@code mainQuery} — FAQ 匹配、BM25、Rerank、最终生成的主问题</li>
 *   <li>{@code subQueries} — 仅用于 Milvus 稠密向量多次检索（多角度召回）</li>
 *   <li>{@code keywords} — 当前仅打日志，尚未接入 BM25</li>
 * </ul>
 */
@Data
public class QueryExpansion {

    /** 改写后的核心问句，检索与生成的主入口 */
    private String mainQuery;

    /** 2~3 个子问题，覆盖不同表述角度，只参与 Dense 召回 */
    private List<String> subQueries;

    /** 3~6 个关键词，预留扩展（暂未参与召回） */
    private List<String> keywords;
}
