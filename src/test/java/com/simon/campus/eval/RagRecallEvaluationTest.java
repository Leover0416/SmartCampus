package com.simon.campus.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.model.dto.RecallCandidate;
import com.simon.campus.service.ingest.VisibilityPolicy;
import com.simon.campus.service.rag.FaqMatcher;
import com.simon.campus.service.rag.MultiRouteRecaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 【评测】离线召回评测：FAQ 短路 + RAG Recall@3。
 * <p>
 * 依赖：MySQL（FAQ 数据）、Redis、Milvus、MinIO、BAILIAN_API_KEY（FAQ 向量匹配需 Embedding）。
 * <p>
 * 运行：
 * <pre>
 * export BAILIAN_API_KEY=sk-xxx
 * mvn test -Dtest=RagRecallEvaluationTest -Deval.integration=true
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "eval.integration", matches = "true")
class RagRecallEvaluationTest {

    @Autowired
    private FaqMatcher faqMatcher;

    @Autowired
    private MultiRouteRecaller multiRouteRecaller;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("FAQ 短路 + RAG Recall@3 评测")
    void evaluateRecall() throws Exception {
        List<EvalCase> cases = loadCases();
        int faqTotal = 0;
        int faqHit = 0;
        int ragTotal = 0;
        int ragHitAt3 = 0;

        System.out.println("\n========== 召回评测（eval-questions.json）==========\n");

        for (EvalCase c : cases) {
            if ("FAQ".equalsIgnoreCase(c.type)) {
                faqTotal++;
                FaqMatcher.FaqMatchResult result = faqMatcher.match(c.query);
                boolean ok = result.shortCircuited()
                    && result.directAnswer() != null
                    && result.directAnswer().contains(c.expectContains);
                if (ok) {
                    faqHit++;
                }
                System.out.printf("[%s] FAQ %-6s %s → shortCircuit=%s contains「%s」%n",
                    ok ? "PASS" : "FAIL", c.id, c.query, result.shortCircuited(), c.expectContains);
            } else if ("RAG".equalsIgnoreCase(c.type)) {
                ragTotal++;
                QueryExpansion expansion = new QueryExpansion();
                expansion.setMainQuery(c.query);
                expansion.setSubQueries(List.of());
                expansion.setKeywords(List.of());

                MultiRouteRecaller.RecallResult recall = multiRouteRecaller.recall(
                    expansion, VisibilityPolicy.STUDENT
                );
                List<RecallCandidate> top3 = recall.candidates().stream().limit(3).toList();
                boolean ok = top3.stream().anyMatch(candidate -> matchesKeyword(candidate, c.expectDocKeyword));
                if (ok) {
                    ragHitAt3++;
                }
                System.out.printf("[%s] RAG  %-6s %s → Recall@3=%s keyword「%s」%n",
                    ok ? "PASS" : "FAIL", c.id, c.query, ok, c.expectDocKeyword);
                if (!ok && !top3.isEmpty()) {
                    top3.forEach(t -> System.out.printf("       top: %s | %s%n",
                        t.getDocTitle(), truncate(t.getContent(), 40)));
                }
            }
        }

        System.out.println("\n--- 汇总 ---");
        if (faqTotal > 0) {
            double faqRate = round1(100.0 * faqHit / faqTotal);
            System.out.printf("FAQ 短路命中率: %d/%d = %s%%%n", faqHit, faqTotal, faqRate);
        }
        if (ragTotal > 0) {
            double recallAt3 = round1(100.0 * ragHitAt3 / ragTotal);
            System.out.printf("RAG Recall@3:   %d/%d = %s%%%n", ragHitAt3, ragTotal, recallAt3);
        }
        System.out.println("\n（样本较少仅供演示，建议扩充 eval-questions.json 至 30+ 条）\n");
    }

    private boolean matchesKeyword(RecallCandidate candidate, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String title = candidate.getDocTitle() == null ? "" : candidate.getDocTitle();
        String content = candidate.getContent() == null ? "" : candidate.getContent();
        String heading = candidate.getHeadingPath() == null ? "" : candidate.getHeadingPath();
        return title.contains(keyword) || content.contains(keyword) || heading.contains(keyword);
    }

    private List<EvalCase> loadCases() throws Exception {
        try (InputStream in = new ClassPathResource("eval/eval-questions.json").getInputStream()) {
            List<EvalCase> list = objectMapper.readValue(in, new TypeReference<>() {});
            return list != null ? list : new ArrayList<>();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static class EvalCase {
        public String id;
        public String query;
        public String type;
        public String expectContains;
        public String expectDocKeyword;
    }
}
