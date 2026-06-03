package com.simon.campus.service.tool;

import com.simon.campus.model.entity.HumanTicket;
import com.simon.campus.service.admin.HumanHandoffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 【导读】LLM 工具「create_human_ticket」的实现：对话中自动创建人工工单。
 * 注意：当前 {@code appendNoticeMessages=false}，不在此处写 chat_messages，
 * 依赖 ChatController.persistMessages 保存当轮对话；若 DB 失败会导致老师端看不到记录。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HumanTicketTool {

    private final HumanHandoffService handoffService;

    public ToolResult create(String sessionId, Long userId, String summary, String urgency) {
        try {
            String resolvedUrgency = urgency == null ? "MEDIUM" :
                switch (urgency.toLowerCase()) {
                    case "high", "高", "紧急" -> "HIGH";
                    case "low", "低" -> "LOW";
                    default -> "MEDIUM";
                };

            HumanTicket ticket = handoffService.requestHandoff(sessionId, userId, summary, resolvedUrgency, false);

            return ToolResult.builder()
                .success(true)
                .toolName("create_human_ticket")
                .params(Map.of("session_id", sessionId != null ? sessionId : "",
                               "urgency", resolvedUrgency))
                .data(Map.of("ticketNo", ticket.getTicketNo(), "status", ticket.getStatus()))
                .summary("已为您创建人工服务工单 " + ticket.getTicketNo() + "，紧急程度：" + resolvedUrgency)
                .dataSource("工单系统")
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();
        } catch (Exception e) {
            log.error("HumanTicketTool error: {}", e.getMessage());
            return ToolResult.builder().success(false).toolName("create_human_ticket")
                .error("创建工单失败：" + e.getMessage()).build();
        }
    }
}
