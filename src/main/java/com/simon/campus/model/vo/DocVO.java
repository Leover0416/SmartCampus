package com.simon.campus.model.vo;

import com.simon.campus.service.ingest.VisibilityPolicy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocVO {
    private String docId;
    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String categoryCode;
    private String status;
    private Integer accessLevel;
    private String accessLevelName;
    private Integer parentChunkCount;
    private Integer childChunkCount;
    private String errorMsg;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static String accessLevelName(Integer level) {
        return VisibilityPolicy.label(level);
    }
}
