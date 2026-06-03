package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("child_chunks")
public class ChildChunk {

    @TableId
    private String childId;

    private String parentId;
    private String docId;
    private String docTitle;
    private String headingPath;
    private String content;
    private Integer chunkIndex;
    private Integer startOffset;
    private Integer endOffset;
    private Integer tokenCount;
    private Integer pageStart;
    private Integer pageEnd;
    private String category;

    /** 继承自所属文档的可见范围：0=全部 1/2=教师 3=学生 */
    private Integer accessLevel;
}
