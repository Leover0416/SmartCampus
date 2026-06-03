package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("faq_pairs")
public class FaqPair {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private String category;
    private String keywords;
    private String priority;
    private Integer enabled;
    private Integer hitCount;
    private String embeddingJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
