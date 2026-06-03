package com.simon.campus.service.admin;

import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.FaqPairMapper;
import com.simon.campus.mapper.FaqSuggestionMapper;
import com.simon.campus.model.entity.FaqPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FaqSuggestionService - 推荐挖掘")
class FaqSuggestionServiceTest {

    @Test
    @DisplayName("过滤已有 FAQ，并附带样例回答")
    void listSuggestionsFiltersExistingFaq() {
        FaqSuggestionMapper suggestionMapper = mock(FaqSuggestionMapper.class);
        FaqPairMapper faqPairMapper = mock(FaqPairMapper.class);
        ChatMessageMapper chatMessageMapper = mock(ChatMessageMapper.class);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", "如何申请成绩复核？");
        row.put("askCount", 3);
        row.put("intent", "POLICY_QA");
        row.put("avgMs", 7200L);
        row.put("lastAskedAt", LocalDateTime.now());
        row.put("weakRecallCount", 3);
        row.put("slowCount", 2);

        when(suggestionMapper.selectCandidateQueries(any(), eq(1), anyInt()))
            .thenReturn(List.of(row));

        FaqPair existing = new FaqPair();
        existing.setQuestion("挂科了怎么办？");
        when(faqPairMapper.findAllEnabled()).thenReturn(List.of(existing));
        when(chatMessageMapper.selectLatestAssistantAnswer("如何申请成绩复核？"))
            .thenReturn("请联系教务处办理成绩复核。");

        FaqSuggestionService service = new FaqSuggestionService(
            suggestionMapper, faqPairMapper, chatMessageMapper);

        var result = service.listSuggestions(7, 1, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestion()).isEqualTo("如何申请成绩复核？");
        assertThat(result.get(0).getReason()).isEqualTo("知识库未召回");
        assertThat(result.get(0).getSampleAnswer()).contains("教务处");
    }
}
