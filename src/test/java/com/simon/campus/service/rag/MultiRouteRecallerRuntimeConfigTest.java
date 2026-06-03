package com.simon.campus.service.rag;

import com.simon.campus.model.dto.QueryExpansion;
import com.simon.campus.service.admin.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MultiRouteRecaller - 安全策略配置")
class MultiRouteRecallerRuntimeConfigTest {

    @Test
    @DisplayName("召回数量从 system_config 读取")
    void recallUsesRuntimeTopKSettings() {
        DenseRetriever denseRetriever = mock(DenseRetriever.class);
        BM25Retriever bm25Retriever = mock(BM25Retriever.class);
        FaqMatcher faqMatcher = mock(FaqMatcher.class);
        SystemConfigService configService = mock(SystemConfigService.class);
        when(configService.getInt("rag.topk.child", 20)).thenReturn(9);
        when(configService.getInt("rag.topk.child_sub", 7)).thenReturn(4);
        when(faqMatcher.match("主问题")).thenReturn(
            new FaqMatcher.FaqMatchResult(false, null, List.of()));
        when(denseRetriever.retrieve("主问题", 0, 9)).thenReturn(List.of());
        when(denseRetriever.retrieve("子问题", 0, 4)).thenReturn(List.of());
        when(bm25Retriever.retrieve("主问题", 0, 9)).thenReturn(List.of());

        MultiRouteRecaller recaller = new MultiRouteRecaller(
            denseRetriever, bm25Retriever, faqMatcher, configService);
        QueryExpansion expansion = new QueryExpansion();
        expansion.setMainQuery("主问题");
        expansion.setSubQueries(List.of("子问题"));

        recaller.recall(expansion, 0);

        verify(denseRetriever).retrieve("主问题", 0, 9);
        verify(denseRetriever).retrieve("子问题", 0, 4);
        verify(bm25Retriever).retrieve("主问题", 0, 9);
    }
}
