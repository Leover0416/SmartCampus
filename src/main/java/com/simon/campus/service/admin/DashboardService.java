package com.simon.campus.service.admin;

import com.simon.campus.mapper.DashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper dashboardMapper;

    public Map<String, Object> getDashboard(int days) {
        int normalizedDays = Math.max(1, Math.min(days, 365));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(normalizedDays);
        LocalDateTime prevSince = since.minusDays(normalizedDays);
        Map<String, Object> result = new LinkedHashMap<>();

        // ── Core metrics ──────────────────────────────────────────────────────
        long totalRequests = dashboardMapper.countTotalRequests(since);
        long previousTotalRequests = dashboardMapper.countTotalRequestsBetween(prevSince, since);
        long faqHits = dashboardMapper.countFaqHits(since);
        double faqHitRate = totalRequests > 0 ? Math.round((double) faqHits / totalRequests * 1000) / 10.0 : 0;
        long knowledgeHits = dashboardMapper.countKnowledgeHits(since);
        long knowledgeReferences = dashboardMapper.countKnowledgeReferences(since);
        long assistantMessages = dashboardMapper.countAssistantMessages(since);
        long assistantMessagesWithRefs = dashboardMapper.countAssistantMessagesWithRefs(since);
        long humanRequests = dashboardMapper.countHumanRequests(since);
        long humanTickets = dashboardMapper.countHumanTickets(since);
        long previousHumanRequests = dashboardMapper.countHumanRequestsBetween(prevSince, since);
        long todaySessions = dashboardMapper.countSessionsToday();
        long yesterdaySessions = dashboardMapper.countSessionsBetween(now.minusDays(1).toLocalDate().atStartOfDay(), now.toLocalDate().atStartOfDay());

        Double avgMs = dashboardMapper.selectAvgResponseMs(since);
        Double previousAvgMs = dashboardMapper.selectAvgResponseMsBetween(prevSince, since);
        Double avgRating = dashboardMapper.selectAvgRating(since);

        double knowledgeHitRate = totalRequests > 0 ? round1((double) knowledgeHits / totalRequests * 100) : 0;
        double citationRate = assistantMessages > 0 ? round1((double) assistantMessagesWithRefs / assistantMessages * 100) : 0;
        double transferRate = totalRequests > 0 ? round1((double) Math.max(humanRequests, humanTickets) / totalRequests * 100) : 0;
        double previousTransferRate = previousTotalRequests > 0 ? round1((double) previousHumanRequests / previousTotalRequests * 100) : 0;
        double satisfaction = avgRating != null && avgRating > 0 ? round1(avgRating / 5.0 * 100) : 0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("sessionsToday",  todaySessions);
        metrics.put("messagesToday",  dashboardMapper.countMessagesToday());
        metrics.put("pendingTickets", dashboardMapper.countPendingTickets());
        metrics.put("readyDocs",      dashboardMapper.countReadyDocs());
        metrics.put("activeFaqs",     dashboardMapper.countActiveFaqs());
        metrics.put("faqHitRate",     faqHitRate);
        metrics.put("avgResponseMs",  avgMs != null ? Math.round(avgMs) : 0);
        metrics.put("totalRequests",  totalRequests);
        metrics.put("satisfaction",   satisfaction);
        metrics.put("transferRate",   transferRate);
        metrics.put("knowledgeHitRate", knowledgeHitRate);
        metrics.put("citationRate", citationRate);
        metrics.put("knowledgeHitCount", knowledgeHits);
        metrics.put("knowledgeReferenceCount", knowledgeReferences);
        metrics.put("totalRequestsTrend", trendPercent(totalRequests, previousTotalRequests));
        metrics.put("sessionsTodayTrend", trendPercent(todaySessions, yesterdaySessions));
        metrics.put("transferRateTrend", round1(transferRate - previousTransferRate));
        metrics.put("avgResponseMsTrend", previousAvgMs != null ? Math.round(avgMs != null ? avgMs - previousAvgMs : -previousAvgMs) : 0);
        result.put("metrics", metrics);

        // ── Trend charts ──────────────────────────────────────────────────────
        result.put("sessionTrend", dashboardMapper.selectSessionTrend(since));
        result.put("messageTrend", dashboardMapper.selectMessageTrend(since));
        result.put("uniqueUserTrend", dashboardMapper.selectUniqueUserTrend(since));

        // ── Intent distribution ───────────────────────────────────────────────
        result.put("intentDistribution", dashboardMapper.selectIntentDistribution(since));
        result.put("hotCategories", dashboardMapper.selectHotCategories(since));

        // ── Top queries ───────────────────────────────────────────────────────
        result.put("topQueries", dashboardMapper.selectTopQueries(since, 10));

        // ── Tool call counts ──────────────────────────────────────────────────
        result.put("toolCalls", dashboardMapper.selectToolCalls(since));
        result.put("humanTakeoverTrend", dashboardMapper.selectHumanTakeoverTrend(since));

        return result;
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private double trendPercent(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return round1((double) (current - previous) / previous * 100);
    }
}
