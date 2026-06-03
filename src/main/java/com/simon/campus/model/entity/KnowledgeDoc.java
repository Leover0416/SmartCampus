package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_docs")
public class KnowledgeDoc {

    @TableId(type = IdType.ASSIGN_UUID)
    private String docId;

    private String title;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String minioKey;
    private String categoryCode;

    /** PROCESSING / READY / FAILED */
    private String status;

    /** 0=全部可见  1/2=仅教师可见（兼容旧数据）  3=仅学生可见 */
    private Integer accessLevel;

    private Integer parentChunkCount;
    private Integer childChunkCount;
    private String department;
    private String errorMsg;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
