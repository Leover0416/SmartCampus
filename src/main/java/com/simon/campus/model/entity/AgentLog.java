package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_logs")
public class AgentLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long userId;
    private String intent;
    private String userQuery;
    private String rewrittenQuery;
    private Integer recallCount;
    private Integer rerankCount;
    private Integer parentCount;
    private Integer stage1Ms;
    private Integer stage2Ms;
    private Integer stage3Ms;
    private Integer stage4Ms;
    private Integer stage5Ms;
    private Integer stage6Ms;
    private Integer totalMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String hitDocs;
    private LocalDateTime createdAt;
}
