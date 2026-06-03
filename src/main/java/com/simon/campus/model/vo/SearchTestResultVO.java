package com.simon.campus.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchTestResultVO {
    private String query;
    private int totalHits;
    private List<HitItem> hits;

    @Data
    @Builder
    public static class HitItem {
        private String childId;
        private String parentId;
        private String docTitle;
        private String headingPath;
        private String content;
        private double score;
        private Integer pageStart;
        private String source;  // "dense" | "bm25"
    }
}
