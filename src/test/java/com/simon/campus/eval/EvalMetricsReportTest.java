package com.simon.campus.eval;

import com.simon.campus.service.admin.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【评测】从 agent_logs 打印与数据看板一致的指标 + FAQ/全链路延迟对比。
 * <p>
 * 使用前：先启动后端，用学生账号在对话页多问几轮（FAQ 问题 + 知识库问题），再运行本测试。
 * <p>
 * 运行：cd campus && mvn test -Dtest=EvalMetricsReportTest
 */
@SpringBootTest
class EvalMetricsReportTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("打印评测报告（与数据看板指标 + 延迟分解）")
    void printMetricsReport() {
        int days = 30;
        Map<String, Object> dashboard = dashboardService.getDashboard(days);
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) dashboard.get("metrics");

        System.out.println("\n========== SmartCampus 评测报告（近 " + days + " 天）==========\n");

        printSection("一、与数据看板一致的核心指标");
        printKv("总问答请求数 totalRequests", metrics.get("totalRequests"));
        printKv("FAQ 命中率 faqHitRate", metrics.get("faqHitRate") + " %");
        printKv("知识库命中率 knowledgeHitRate", metrics.get("knowledgeHitRate") + " %");
        printKv("引用率 citationRate", metrics.get("citationRate") + " %");
        printKv("转人工率 transferRate", metrics.get("transferRate") + " %");
        printKv("平均响应耗时 avgResponseMs", metrics.get("avgResponseMs") + " ms");
        printKv("知识库命中次数 knowledgeHitCount", metrics.get("knowledgeHitCount"));
        printKv("启用 FAQ 数 activeFaqs", metrics.get("activeFaqs"));
        printKv("就绪文档数 readyDocs", metrics.get("readyDocs"));

        printSection("二、FAQ 短路 vs 完整 RAG 延迟对比");
        printPathLatency("FAQ 短路", "hit_docs = 'FAQ'");
        printPathLatency("完整 RAG", "intent IN ('POLICY_QA','DOC_SEARCH') AND stage6_ms > 0");
        printPathLatency("教务工具", "hit_docs LIKE 'TOOL:%'");

        printSection("三、完整 RAG 各阶段平均耗时（毫秒）");
        printStageBreakdown();

        printSection("四、按意图分布");
        List<Map<String, Object>> intents = jdbcTemplate.queryForList(
            "SELECT intent, COUNT(*) AS cnt, ROUND(AVG(total_ms)) AS avg_ms " +
                "FROM agent_logs WHERE created_at >= ? GROUP BY intent ORDER BY cnt DESC",
            LocalDateTime.now().minusDays(days)
        );
        if (intents.isEmpty()) {
            System.out.println("  （暂无 agent_logs 数据，请先在对话页提问）");
        } else {
            intents.forEach(row -> System.out.printf("  %-16s count=%-4s avg=%s ms%n",
                row.get("intent"), row.get("cnt"), row.get("avg_ms")));
        }

        printSection("五、简历可引用句式（请替换为上面真实数字）");
        long total = toLong(metrics.get("totalRequests"));
        if (total > 0) {
            System.out.println("  基于 agent_logs 近 " + days + " 天 " + total + " 次请求统计：");
            System.out.println("  FAQ 命中率 " + metrics.get("faqHitRate") + "%，平均响应 "
                + metrics.get("avgResponseMs") + "ms；详见本报告第二节 P95。");
        } else {
            System.out.println("  暂无请求数据。请先登录 student001，多问 FAQ/政策类问题后重跑。");
        }
        System.out.println("\n============================================================\n");
    }

    private void printPathLatency(String label, String whereClause) {
        Long cnt = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agent_logs WHERE " + whereClause + " AND total_ms > 0",
            Long.class
        );
        if (cnt == null || cnt == 0) {
            System.out.println("  " + label + "：无样本");
            return;
        }
        Map<String, Object> agg = jdbcTemplate.queryForMap(
            "SELECT ROUND(AVG(total_ms)) AS avg_ms, MIN(total_ms) AS min_ms, MAX(total_ms) AS max_ms " +
                "FROM agent_logs WHERE " + whereClause + " AND total_ms > 0"
        );
        long p95 = percentileMs(whereClause, 0.95);
        System.out.printf("  %s：n=%d  avg=%s ms  P95=%d ms  min=%s  max=%s%n",
            label, cnt, agg.get("avg_ms"), p95, agg.get("min_ms"), agg.get("max_ms"));
    }

    private long percentileMs(String whereClause, double percentile) {
        List<Integer> values = jdbcTemplate.queryForList(
            "SELECT total_ms FROM agent_logs WHERE " + whereClause + " AND total_ms > 0 ORDER BY total_ms",
            Integer.class
        );
        if (values.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        index = Math.max(0, Math.min(index, values.size() - 1));
        return values.get(index);
    }

    private void printStageBreakdown() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT COUNT(*) AS cnt, " +
                "ROUND(AVG(stage1_ms)) AS s1, ROUND(AVG(stage2_ms)) AS s2, " +
                "ROUND(AVG(stage3_ms)) AS s3, ROUND(AVG(stage4_ms)) AS s4, " +
                "ROUND(AVG(stage5_ms)) AS s5, ROUND(AVG(stage6_ms)) AS s6, " +
                "ROUND(AVG(total_ms)) AS total " +
                "FROM agent_logs " +
                "WHERE intent IN ('POLICY_QA','DOC_SEARCH') AND stage6_ms > 0 AND total_ms > 0"
        );
        if (rows.isEmpty() || toLong(rows.get(0).get("cnt")) == 0) {
            System.out.println("  （无完整 RAG 样本，请提问政策/文档类问题）");
            return;
        }
        Map<String, Object> r = rows.get(0);
        System.out.printf("  样本数 n=%s%n", r.get("cnt"));
        System.out.printf("  Stage1 上下文融合: %s ms%n", r.get("s1"));
        System.out.printf("  Stage2 查询改写:   %s ms%n", r.get("s2"));
        System.out.printf("  Stage3 多路召回:   %s ms%n", r.get("s3"));
        System.out.printf("  Stage4 Rerank:     %s ms%n", r.get("s4"));
        System.out.printf("  Stage5 上下文组装: %s ms%n", r.get("s5"));
        System.out.printf("  Stage6 流式生成:   %s ms%n", r.get("s6"));
        System.out.printf("  总耗时 total:      %s ms%n", r.get("total"));
    }

    private static void printSection(String title) {
        System.out.println(title);
        System.out.println("-".repeat(Math.min(title.length(), 60)));
    }

    private static void printKv(String key, Object value) {
        System.out.printf("  %-32s %s%n", key + ":", value);
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
