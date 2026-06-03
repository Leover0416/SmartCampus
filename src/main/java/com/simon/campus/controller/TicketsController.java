package com.simon.campus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.simon.campus.common.R;
import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.HumanTicketMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.HumanTicket;
import com.simon.campus.service.admin.HumanHandoffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 【导读】老师工作台 REST API。
 * <ul>
 *   <li>GET /tickets、/stats：工单列表与统计</li>
 *   <li>GET /tickets/{id}/messages：按工单的 sessionId 查 chat_messages（对话记录区）</li>
 *   <li>POST /tickets/{id}/reply：老师回复（需 TEACHER 或 ADMIN 角色）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketsController {

    private final HumanTicketMapper ticketMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final HumanHandoffService handoffService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Query ─────────────────────────────────────────────────────────────────

    @GetMapping
    public R<List<HumanTicket>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) String issueType) {
        LambdaQueryWrapper<HumanTicket> qw = new LambdaQueryWrapper<HumanTicket>()
            .orderByDesc(HumanTicket::getCreatedAt);
        if (StringUtils.hasText(status))    qw.eq(HumanTicket::getStatus, status);
        if (StringUtils.hasText(urgency))   qw.eq(HumanTicket::getUrgency, urgency);
        if (StringUtils.hasText(issueType)) qw.eq(HumanTicket::getIssueType, issueType);
        return R.ok(ticketMapper.selectList(qw));
    }

    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(Map.of(
            "PENDING",  countByStatus("PENDING"),
            "HANDLING", countByStatus("HANDLING"),
            "RESOLVED", countByStatus("RESOLVED"),
            "CLOSED",   countByStatus("CLOSED")
        ));
    }

    @GetMapping("/{id}")
    public R<HumanTicket> getById(@PathVariable Long id) {
        return R.ok(ticketMapper.selectById(id));
    }

    @GetMapping("/{id}/messages")
    public R<List<ChatMessage>> getMessages(@PathVariable Long id) {
        HumanTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) return R.ok(List.of());
        return R.ok(chatMessageMapper.selectList(
            new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, ticket.getSessionId())
                .orderByAsc(ChatMessage::getId)));
    }

    // ── State transitions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<ChatMessage> reply(@PathVariable Long id, @RequestBody ReplyRequest request) {
        ChatMessage message = handoffService.reply(id, currentUserId(), request.content());
        pushUpdate(id, "HANDLING");
        return R.ok(message);
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> resolve(@PathVariable Long id) {
        ticketMapper.update(null, new LambdaUpdateWrapper<HumanTicket>()
            .eq(HumanTicket::getId, id)
            .set(HumanTicket::getStatus, "RESOLVED")
            .set(HumanTicket::getUpdatedAt, LocalDateTime.now()));
        pushUpdate(id, "RESOLVED");
        return R.ok(null);
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public R<Void> close(@PathVariable Long id) {
        ticketMapper.update(null, new LambdaUpdateWrapper<HumanTicket>()
            .eq(HumanTicket::getId, id)
            .set(HumanTicket::getStatus, "CLOSED")
            .set(HumanTicket::getUpdatedAt, LocalDateTime.now()));
        pushUpdate(id, "CLOSED");
        return R.ok(null);
    }

    @PutMapping("/{id}/rate")
    public R<Void> rate(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer rating = body.get("rating");
        if (rating == null || rating < 1 || rating > 5) return R.ok(null);
        ticketMapper.update(null, new LambdaUpdateWrapper<HumanTicket>()
            .eq(HumanTicket::getId, id)
            .set(HumanTicket::getRating, rating)
            .set(HumanTicket::getUpdatedAt, LocalDateTime.now()));
        return R.ok(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> delete(@PathVariable Long id) {
        ticketMapper.deleteById(id);
        return R.ok(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long countByStatus(String status) {
        return ticketMapper.selectCount(new LambdaQueryWrapper<HumanTicket>()
            .eq(HumanTicket::getStatus, status));
    }

    private void pushUpdate(Long ticketId, String status) {
        try {
            messagingTemplate.convertAndSend("/topic/tickets",
                Map.of("ticketId", ticketId, "status", status));
        } catch (Exception e) {
            log.warn("WebSocket push failed: {}", e.getMessage());
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object cred = auth.getCredentials();
        return cred instanceof Long l ? l : 0L;
    }

    private record ReplyRequest(String content) {}
}
