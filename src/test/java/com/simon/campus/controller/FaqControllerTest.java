package com.simon.campus.controller;

import com.simon.campus.service.admin.FaqService;
import com.simon.campus.service.admin.FaqSuggestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FaqController - FAQ 查询")
class FaqControllerTest {

    @Test
    @DisplayName("列表查询会把优先级筛选传给服务层")
    void listPassesPriorityFilter() {
        FaqService faqService = mock(FaqService.class);
        FaqSuggestionService faqSuggestionService = mock(FaqSuggestionService.class);
        when(faqService.list("绩点", "academic", 1, "HIGH")).thenReturn(List.of());
        FaqController controller = new FaqController(faqService, faqSuggestionService);

        controller.list("绩点", "academic", 1, "HIGH");

        verify(faqService).list("绩点", "academic", 1, "HIGH");
    }
}
