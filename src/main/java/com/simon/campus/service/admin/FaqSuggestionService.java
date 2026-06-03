package com.simon.campus.service.admin;

import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.mapper.FaqSuggestionMapper;
import com.simon.campus.model.entity.FaqPair;
import com.simon.campus.model.vo.FaqSuggestionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mines high-frequency questions from agent_logs that did not hit FAQ,
 * for teachers to adopt into faq_pairs.
 */
@Service
@RequiredArgsConstructor
public class FaqSuggestionService {

    private static final int SAMPLE_ANSWER_MAX = 2000;

    private final FaqSuggestionMapper faqSuggestionMapper;
    private final FaqPairMapper faqPairMapper;
    private final ChatMessageMapper chatMessageMapper;

    public List<FaqSuggestionVO> listSuggestions(int days, int minCount, int limit) {
        int safeDays = Math.max(1, Math.min(days, 90));
        int safeMin = Math.max(1, minCount);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDateTime since = LocalDateTime.now().minusDays(safeDays);

        Set<String> existingQuestions = faqPairMapper.findAllEnabled().stream()
            .map(FaqPair::getQuestion)
            .filter(Objects::nonNull)
            .map(this::normalize)
            .collect(Collectors.toSet());

        List<FaqSuggestionVO> result = new ArrayList<>();
        for (Map<String, Object> row : faqSuggestionMapper.selectCandidateQueries(since, safeMin, safeLimit * 2)) {
            String question = Objects.toString(row.get("question"), "").trim();
            if (question.isEmpty() || existingQuestions.contains(normalize(question))) {
                continue;
            }

            int askCount = toInt(row.get("askCount"));
            long avgMs = toLong(row.get("avgMs"));
            int weakRecall = toInt(row.get("weakRecallCount"));
            int slowCount = toInt(row.get("slowCount"));

            String sampleAnswer = trimSample(chatMessageMapper.selectLatestAssistantAnswer(question));
            FaqSuggestionVO vo = FaqSuggestionVO.builder()
                .question(question)
                .askCount(askCount)
                .intent(Objects.toString(row.get("intent"), ""))
                .avgMs(avgMs)
                .lastAskedAt(toLocalDateTime(row.get("lastAskedAt")))
                .reason(buildReason(weakRecall, askCount, slowCount, avgMs))
                .sampleAnswer(sampleAnswer)
                .build();
            result.add(vo);
            if (result.size() >= safeLimit) break;
        }
        return result;
    }

    private String buildReason(int weakRecall, int askCount, int slowCount, long avgMs) {
        if (weakRecall >= askCount) {
            return "知识库未召回";
        }
        if (slowCount > 0 || avgMs >= 5000) {
            return "全链路较慢";
        }
        return "未命中FAQ";
    }

    private String trimSample(String answer) {
        if (answer == null || answer.isBlank()) return "";
        String trimmed = answer.strip();
        return trimmed.length() <= SAMPLE_ANSWER_MAX
            ? trimmed
            : trimmed.substring(0, SAMPLE_ANSWER_MAX) + "...";
    }

    private String normalize(String q) {
        return q.strip().toLowerCase(Locale.ROOT);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }

    private LocalDateTime toLocalDateTime(Object v) {
        if (v instanceof LocalDateTime dt) return dt;
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
