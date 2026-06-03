package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("knowledge_categories")
public class KnowledgeCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;
    private Integer status;
    private Integer sortOrder;
}
