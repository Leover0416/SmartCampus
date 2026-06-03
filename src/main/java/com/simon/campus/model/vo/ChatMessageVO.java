package com.simon.campus.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatMessageVO {
    private Long id;
    private String role;
    private String content;
    private String intent;
    private String imageUrl;
    private String imageName;
    private List<SourceRefVO> sourceRefs;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class SourceRefVO {
        private String docTitle;
        private String headingPath;
        private Integer pageStart;
    }
}
