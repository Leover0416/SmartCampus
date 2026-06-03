package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("parent_chunks")
public class ParentChunk {

    @TableId
    private String parentId;

    private String docId;
    private String docTitle;
    private String headingPath;
    private String content;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer tokenCount;
    private String category;

    /** 继承自所属文档的 access_level */
    private Integer accessLevel;

    private LocalDateTime createdAt;
}
