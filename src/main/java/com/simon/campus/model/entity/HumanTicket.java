package com.simon.campus.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("human_tickets")
public class HumanTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String sessionId;
    private Long userId;
    private Long assignedTo;
    private String subject;
    private String issueType;
    private String urgency;
    private String status;
    private String aiSummary;
    private Integer rating;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
