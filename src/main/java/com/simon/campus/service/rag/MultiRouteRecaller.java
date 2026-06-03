package com.simon.campus.service.rag;

import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.service.admin.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RAG Stage 3：多路并行召回 + RRF 融合。
 * <p>
 * 三路来源：
 * <ol>
 *   <li><b>Dense（Milvus）</b> — mainQuery TopK + 每个 subQuery 各 TopK_sub</li>
 *   <li><b>BM25（MySQL 倒排）</b> — 仅 mainQuery</li>
 *   <li><b>FAQ</b> — 仅 mainQuery；≥0.92 短路；0.85~0.92 作为候选进 RRF</li>
 * </ol>
 * 子查询 subQueries 只增加 Milvus 检索次数，不参与 BM25 / FAQ。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiRouteRecaller {

    /** RRF 公式分母常数 k，越大则排名靠后项贡献衰减越慢 */
    private static final int RRF_K = 60;

    /** RRF 融合后保留的 Child 候选上限，交给 Stage 4 Rerank */
    private static final int RRF_OUTPUT_TOP_K = 20;

    private final DenseRetriever denseRetriever;
    private final BM25Retriever bm25Retriever;
    private final FaqMatcher faqMatcher;
    private final SystemConfigService configService;

    /** Dense 与 BM25 并行检索用（池大小 3，当前实际并发 2 路 Future） */
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    /**
     * @param faqShortCircuited true 表示 FAQ 精确命中，直接返回答案、不再走 RAG
     * @param faqAnswer         短路时的 FAQ 标准答案
     * @param candidates        未短路时的融合候选 Child 列表（已 RRF 排序）
     */
    public record RecallResult(
        boolean faqShortCircuited,
        String faqAnswer,
        List<RecallCandidate> candidates
    ) {}

    /**
     * @param expansion       Stage 2 改写结果（含 mainQuery / subQueries）
     * @param userAccessLevel 学生/教师可见性，过滤 Milvus、BM25 结果
     */
    public RecallResult recall(QueryExpansion expansion, int userAccessLevel) {
        String mainQuery = expansion.getMainQuery();
        // 主问与子问使用不同 TopK：主问权重大，子问补充角度
        int mainTopK = configService.getInt("rag.topk.child", 20);
        int subTopK = configService.getInt("rag.topk.child_sub", 7);

        // ── 3.1 FAQ 优先：只用 mainQuery，不用 subQueries ──
        FaqMatcher.FaqMatchResult faqResult = faqMatcher.match(mainQuery);
        log.info("[AGENT_FLOW] step=faq_match query={} shortCircuited={} candidates={}",
            abbreviate(mainQuery), faqResult.shortCircuited(), faqResult.candidates().size());
        if (faqResult.shortCircuited()) {
            // 相似度 ≥ exact_thresh（默认 0.92）→ 直接返回 FAQ 答案，跳过后续 RAG
            log.debug("FAQ short-circuit matched for: {}", mainQuery);
            return new RecallResult(true, faqResult.directAnswer(), Collections.emptyList());
        }

        // ── 3.2 并行召回：Dense(main + subs) 与 BM25(main) ──
        CompletableFuture<List<RecallCandidate>> denseFuture = CompletableFuture.supplyAsync(() -> {
            List<RecallCandidate> all = new ArrayList<>(
                // 主问：Milvus 向量检索 Top mainTopK
                denseRetriever.retrieve(mainQuery, userAccessLevel, mainTopK));
            // 子查询：每个 subQuery 单独 embed + 检索 Top subTopK，结果追加合并
            for (String sub : expansion.getSubQueries()) {
                all.addAll(denseRetriever.retrieve(sub, userAccessLevel, subTopK));
            }
            return all;
        }, executor);

        CompletableFuture<List<RecallCandidate>> bm25Future = CompletableFuture.supplyAsync(() ->
            // BM25 仅检索 mainQuery；keywords / subQueries 当前未使用
            bm25Retriever.retrieve(mainQuery, userAccessLevel, mainTopK), executor);

        // FAQ 未短路但相似度在 candidate 区间（0.85~0.92）的条目，作为第三路进 RRF
        List<RecallCandidate> faqCandidates = faqResult.candidates();

        List<RecallCandidate> denseResults;
        List<RecallCandidate> bm25Results;
        try {
            denseResults = denseFuture.get();
            bm25Results = bm25Future.get();
        } catch (Exception e) {
            log.error("Recall futures failed: {}", e.getMessage());
            denseResults = Collections.emptyList();
            bm25Results = Collections.emptyList();
        }

        // ── 3.3 同一路内按 childId 去重（main 与子问可能命中同一 Child）──
        List<RecallCandidate> denseDeduped = dedup(denseResults);
        List<RecallCandidate> bm25Deduped  = dedup(bm25Results);

        // ── 3.4 RRF 融合 Dense + BM25 + FAQ 候选 → Top 20 ──
        List<RecallCandidate> merged = rrfMerge(denseDeduped, bm25Deduped, faqCandidates);
        log.info("[AGENT_FLOW] step=multi_recall dense={} bm25={} faqCandidates={} rrfOut={} hits={}",
            denseDeduped.size(), bm25Deduped.size(), faqCandidates.size(), merged.size(), summarizeHits(merged));

        return new RecallResult(false, null, merged);
    }

    /**
     * Reciprocal Rank Fusion：多路有序列表按排名加权求和。
     * <p>
     * 每个候选在第 rank 位的贡献为 {@code 1 / (k + rank + 1)}，同一 childId 在多路出现则累加。
     * 最终按 RRF 分降序取 Top {@link #RRF_OUTPUT_TOP_K}。
     */
    @SafeVarargs
    private List<RecallCandidate> rrfMerge(List<RecallCandidate>... lists) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RecallCandidate> candidates = new LinkedHashMap<>();

        for (List<RecallCandidate> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RecallCandidate c = list.get(rank);
                double contrib = 1.0 / (RRF_K + rank + 1);
                rrfScores.merge(c.getChildId(), contrib, Double::sum);
                candidates.putIfAbsent(c.getChildId(), c);
            }
        }

        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(RRF_OUTPUT_TOP_K)
            .map(e -> {
                RecallCandidate c = candidates.get(e.getKey());
                c.setScore(e.getValue()); // 覆盖原 Dense/BM25 分数为 RRF 融合分
                return c;
            })
            .toList();
    }

    /** 同一路召回内保留首次出现的 Child（LinkedHashMap 保序） */
    private List<RecallCandidate> dedup(List<RecallCandidate> list) {
        Map<String, RecallCandidate> seen = new LinkedHashMap<>();
        for (RecallCandidate c : list) {
            seen.putIfAbsent(c.getChildId(), c);
        }
        return new ArrayList<>(seen.values());
    }

    private String summarizeHits(List<RecallCandidate> candidates) {
        return candidates.stream()
            .limit(5)
            .map(c -> c.getDocTitle() + "/" + c.getChildId() + "/" + c.getScore())
            .toList()
            .toString();
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 1000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
