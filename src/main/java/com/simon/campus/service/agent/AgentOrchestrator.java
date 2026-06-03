package com.simon.campus.service.agent;

import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.model.entity.AgentLog;
import com.simon.campus.service.rag.*;
import com.simon.campus.service.ingest.VisibilityPolicy;
import com.simon.campus.service.tool.ToolCaller;
import com.simon.campus.service.tool.ToolResult;
import com.simon.campus.session.SessionContext;
import com.simon.campus.session.SessionManager;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 【导读】AI 对话总编排器（学生发消息后的核心逻辑）。
 * <pre>
 * 1. ContextMerger 多轮指代消解
 * 2. FaqMatcher 向量短路（优先于意图路由，避免「选课」等关键词误入 ACADEMIC_TOOL）
 * 3. IntentRouter 意图分类 → HUMAN / CHITCHAT / ACADEMIC_TOOL / POLICY_QA(DOC_SEARCH)
 * 4. 分支：
 *    - HUMAN：转人工话术
 *    - CHITCHAT：闲聊流式生成
 *    - ACADEMIC_TOOL：ToolCaller 调校历/选课/工单等
 *    - 默认：RAG 六阶段（改写→召回→重排→组装→生成）
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {

    private final SessionManager sessionManager;
    private final ContextMerger contextMerger;
    private final QueryRewriter queryRewriter;
    private final MultiRouteRecaller recaller;
    private final Reranker reranker;
    private final ParentChildContextAssembler assembler;
    private final RagGenerator ragGenerator;
    private final IntentRouter intentRouter;
    private final AgentLogService agentLogService;
    private final ToolCaller toolCaller;
    private final FaqMatcher faqMatcher;

    @Data
    @AllArgsConstructor
    public static class OrchestrationResult {
        private final String answer;
        private final String intent;
        private final List<ParentChildContextAssembler.SourceRef> sourceRefs;
        private final boolean faqShortCircuited;
        /** Non-null when an ACADEMIC_TOOL was invoked */
        private final ToolResult toolResult;
    }

    public OrchestrationResult handleStream(
            String rawQuery, String sessionId,
            Long userId, String username, String role,
            Consumer<String> onToken) {

        long t0 = System.currentTimeMillis();
        SessionContext session = sessionManager.getOrCreate(sessionId, userId, username, role);
        log.info("[AGENT_FLOW] session={} step=start role={} rawQuery={}",
            session.getSessionId(), role, abbreviate(rawQuery));

        AgentLog logEntry = new AgentLog();
        logEntry.setSessionId(session.getSessionId());
        logEntry.setUserId(userId);
        logEntry.setUserQuery(rawQuery);

        // Stage 1: Context merge
        long s1 = System.currentTimeMillis();
        String mergedQuery = contextMerger.merge(rawQuery, session);
        logEntry.setStage1Ms((int)(System.currentTimeMillis() - s1));
        log.info("[AGENT_FLOW] session={} step=context_merge costMs={} mergedQuery={}",
            session.getSessionId(), logEntry.getStage1Ms(), abbreviate(mergedQuery));

        String answer = "";
        List<ParentChildContextAssembler.SourceRef> sourceRefs = List.of();
        boolean faqHit = false;
        ToolResult toolResult = null;
        String intent = "POLICY_QA";

        // FAQ fast path: before intent routing (avoids ACADEMIC_TOOL keyword intercept)
        FaqMatcher.FaqMatchResult faqEarly = faqMatcher.match(mergedQuery);
        if (faqEarly.shortCircuited()) {
            logEntry.setIntent(intent);
            logEntry.setHitDocs("FAQ");
            answer = faqEarly.directAnswer();
            faqHit = true;
            onToken.accept(answer);
            log.info("[AGENT_FLOW] session={} step=faq_early_short_circuit answer={}",
                session.getSessionId(), abbreviate(answer));
        } else {
            intent = intentRouter.route(mergedQuery);
            logEntry.setIntent(intent);
            session.setCurrentIntent(intent);
            log.info("[AGENT_FLOW] session={} step=intent_route intent={}", session.getSessionId(), intent);

            try {
                if ("HUMAN".equals(intent)) {
                    log.info("[AGENT_FLOW] session={} step=human_transfer", session.getSessionId());
                    answer = handleHumanTransfer(onToken, session);
                } else if ("CHITCHAT".equals(intent)) {
                    log.info("[AGENT_FLOW] session={} step=chitchat_generate", session.getSessionId());
                    answer = ragGenerator.chitchatStream(mergedQuery, session, onToken);
                } else if ("ACADEMIC_TOOL".equals(intent)) {
                    log.info("[AGENT_FLOW] session={} step=tool_call", session.getSessionId());
                    ToolCaller.ToolCallResult tcr = toolCaller.call(mergedQuery, session, onToken);
                    answer = tcr.answer();
                    toolResult = tcr.toolResult();
                    if (toolResult != null) logEntry.setHitDocs("TOOL:" + toolResult.getToolName());
                } else {
                    PipelineResult pr = runRagPipeline(mergedQuery, session, logEntry, onToken);
                    answer = pr.answer();
                    sourceRefs = pr.sourceRefs();
                    faqHit = "FAQ".equals(logEntry.getHitDocs());
                }
            } catch (Exception e) {
                log.error("Pipeline failed: {}", e.getMessage(), e);
                answer = "抱歉，处理您的请求时出现错误，请稍后重试。";
                try { onToken.accept(answer); } catch (Exception ignored) {}
            }
        }

        session.addMessage("user", rawQuery);
        session.addMessage("assistant", answer);
        sessionManager.save(session);

        logEntry.setTotalMs((int)(System.currentTimeMillis() - t0));
        agentLogService.save(logEntry);

        log.info("[AGENT_FLOW] session={} step=done intent={} totalMs={} answer={}",
            session.getSessionId(), logEntry.getIntent(), logEntry.getTotalMs(), abbreviate(answer));
        return new OrchestrationResult(answer, logEntry.getIntent(), sourceRefs, faqHit, toolResult);
    }

    private record PipelineResult(
        String answer,
        List<ParentChildContextAssembler.SourceRef> sourceRefs
    ) {}

    /**
     * RAG 六阶段流水线（Stage 2~6）。
     * <pre>
     * Stage2 QueryRewriter   → mainQuery + subQueries + keywords
     * Stage3 MultiRouteRecaller → FAQ / Dense(main+subs) / BM25(main) / RRF
     * Stage4 Reranker        → gte-rerank，query 用 mainQuery
     * Stage5 Assembler     → Child 归并 Parent，取 3~6 段上下文
     * Stage6 RagGenerator    → 流式生成，query 用 mainQuery
     * </pre>
     */
    private PipelineResult runRagPipeline(String mergedQuery, SessionContext session,
                                           AgentLog logEntry, Consumer<String> onToken) throws Exception {
        int userAccessLevel = accessLevelFor(session.getRole());

        // ── Stage 2：LLM 查询扩展（子查询在此生成）──
        long s2 = System.currentTimeMillis();
        QueryExpansion expansion = queryRewriter.rewrite(mergedQuery);
        logEntry.setRewrittenQuery(expansion.getMainQuery());
        logEntry.setStage2Ms((int)(System.currentTimeMillis() - s2));
        log.info("[AGENT_FLOW] session={} step=query_rewrite costMs={} mainQuery={} subQueries={} keywords={}",
            session.getSessionId(), logEntry.getStage2Ms(), abbreviate(expansion.getMainQuery()),
            expansion.getSubQueries(), expansion.getKeywords());

        // ── Stage 3：多路召回 + RRF（subQueries 只进 Milvus）──
        long s3 = System.currentTimeMillis();
        MultiRouteRecaller.RecallResult recallResult = recaller.recall(expansion, userAccessLevel);
        logEntry.setStage3Ms((int)(System.currentTimeMillis() - s3));
        log.info("[AGENT_FLOW] session={} step=recall costMs={} faqShortCircuited={} candidates={}",
            session.getSessionId(), logEntry.getStage3Ms(), recallResult.faqShortCircuited(),
            recallResult.candidates().size());

        if (recallResult.faqShortCircuited()) {
            logEntry.setRecallCount(0);
            logEntry.setHitDocs("FAQ");
            String ans = recallResult.faqAnswer();
            onToken.accept(ans);
            log.info("[AGENT_FLOW] session={} step=faq_short_circuit answer={}",
                session.getSessionId(), abbreviate(ans));
            return new PipelineResult(ans, List.of());
        }

        List<RecallCandidate> candidates = recallResult.candidates();
        logEntry.setRecallCount(candidates.size());

        // ── Stage 4：精排（仅 mainQuery，不用 subQueries）──
        long s4 = System.currentTimeMillis();
        List<RecallCandidate> reranked = reranker.rerank(expansion.getMainQuery(), candidates);
        logEntry.setStage4Ms((int)(System.currentTimeMillis() - s4));
        logEntry.setRerankCount(reranked.size());
        log.info("[AGENT_FLOW] session={} step=rerank costMs={} in={} out={}",
            session.getSessionId(), logEntry.getStage4Ms(), candidates.size(), reranked.size());

        // ── Stage 5：按 parentId 聚合 Child，回捞 Parent 全文 ──
        long s5 = System.currentTimeMillis();
        ParentChildContextAssembler.AssembledContext ctx = assembler.assemble(reranked);
        logEntry.setStage5Ms((int)(System.currentTimeMillis() - s5));
        logEntry.setParentCount(ctx.getSourceRefs().size());
        log.info("[AGENT_FLOW] session={} step=assemble_context costMs={} parents={} context={}",
            session.getSessionId(), logEntry.getStage5Ms(), ctx.getSourceRefs().size(),
            abbreviate(ctx.getContextText()));

        if (!ctx.getSourceRefs().isEmpty()) {
            String docs = ctx.getSourceRefs().stream()
                .map(ParentChildContextAssembler.SourceRef::getDocTitle)
                .distinct().reduce((a, b) -> a + "|" + b).orElse("");
            logEntry.setHitDocs(docs);
        }

        // ── Stage 6：基于参考资料流式生成（mainQuery + 会话 history）──
        long s6 = System.currentTimeMillis();
        String answer = ragGenerator.generateStream(
            expansion.getMainQuery(), ctx.getContextText(), session, onToken);
        logEntry.setStage6Ms((int)(System.currentTimeMillis() - s6));
        log.info("[AGENT_FLOW] session={} step=rag_generate costMs={} answer={}",
            session.getSessionId(), logEntry.getStage6Ms(), abbreviate(answer));

        return new PipelineResult(answer, ctx.getSourceRefs());
    }

    private String handleHumanTransfer(Consumer<String> onToken, SessionContext session) {
        session.setStatus("HUMAN_PENDING");
        String msg = "好的，我已为您发起人工客服请求，请稍候，工作人员将尽快与您联系。";
        onToken.accept(msg);
        return msg;
    }

    private int accessLevelFor(String role) {
        if (role == null) return VisibilityPolicy.STUDENT;
        return switch (role.toUpperCase()) {
            case "ADMIN", "TEACHER" -> VisibilityPolicy.TEACHER;
            default -> VisibilityPolicy.STUDENT;
        };
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 2000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
