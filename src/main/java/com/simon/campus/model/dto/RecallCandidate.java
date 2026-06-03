package com.simon.campus.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecallCandidate {
    private String childId;
    private String parentId;
    private String docId;
    private String docTitle;
    private String headingPath;
    private String content;
    private Integer pageStart;
    private double score;
    private String source; // "dense" | "bm25" | "faq"
}
