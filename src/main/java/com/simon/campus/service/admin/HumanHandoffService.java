package com.simon.campus.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.simon.campus.common.BizException;
import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.ChatSessionMapper;
import com.simon.campus.mapper.HumanTicketMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.ChatSession;
import com.simon.campus.model.entity.HumanTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【导读】人工客服（转人工）核心服务。
 * <ul>
 *   <li>工单表 human_tickets：一条工单对应一个 chat session_id</li>
 *   <li>聊天记录表 chat_messages：学生/AI/老师消息都按 session_id 存储</li>
 *   <li>老师工作台通过 TicketsController 按 ticket.id 查 session_id 再拉消息</li>
 *   <li>{@code appendNoticeMessages=false} 时只建工单不写消息（见 HumanTicketTool）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HumanHandoffService {

    private static final List<String> OPEN_STATUSES = List.of("PENDING", "HANDLING");

    private final HumanTicketMapper ticketMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    public HumanTicket findOpenTicket(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return null;
        return ticketMapper.selectOne(new LambdaQueryWrapper<HumanTicket>()
            .eq(HumanTicket::getSessionId, sessionId)
            .in(HumanTicket::getStatus, OPEN_STATUSES)
            .orderByDesc(HumanTicket::getCreatedAt)
            .last("LIMIT 1"));
    }

    /** 默认会往 chat_messages 写入「申请转人工」及 AI 提示（前端点「转人工」按钮走此路径） */
    public HumanTicket requestHandoff(String sessionId, Long userId, String summary, String urgency) {
        return requestHandoff(sessionId, userId, summary, urgency, true);
    }

    /**
     * 创建或复用未关闭工单。
     * @param appendNoticeMessages true=立即写入 user/assistant 两条 HUMAN_HANDOFF 消息；false=仅建工单
     */
    public HumanTicket requestHandoff(String sessionId, Long userId, String summary, String urgency, boolean appendNoticeMessages) {
        if (!StringUtils.hasText(sessionId)) throw new BizException("会话不存在，无法转人工");
        HumanTicket existing = findOpenTicket(sessionId);
        if (existing != null) return existing;

        ensureSession(sessionId, userId, "人工服务请求");

        String content = StringUtils.hasText(summary) ? summary : "申请转人工";
        if (appendNoticeMessages) {
            appendMessage(sessionId, userId, "user", content, "HUMAN_HANDOFF");
            appendMessage(sessionId, userId, "assistant",
                "已为你转接人工老师。你可以继续在这里补充问题，老师会看到当前会话记录并回复。",
                "HUMAN_HANDOFF");
            touchSession(sessionId, 2);
        }

        HumanTicket ticket = new HumanTicket();
        ticket.setTicketNo("TK" + System.currentTimeMillis());
        ticket.setSessionId(sessionId);
        ticket.setUserId(userId != null ? userId : 0L);
        ticket.setSubject(content.length() > 100 ? content.substring(0, 100) : content);
        ticket.setIssueType("人工服务");
        ticket.setAiSummary("用户申请转人工。老师可查看该会话中的学生与 AI 历史记录，并在同一会话回复。");
        ticket.setUrgency(normalizeUrgency(urgency));
        ticket.setStatus("PENDING");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);
        return ticket;
    }

    public void appendStudentMessage(String sessionId, Long userId, String content) {
        appendMessage(sessionId, userId, "user", content, "HUMAN_HANDOFF");
        touchSession(sessionId, 1);
        HumanTicket ticket = findOpenTicket(sessionId);
        if (ticket != null) {
            ticketMapper.update(null, new UpdateWrapper<HumanTicket>()
                .eq("id", ticket.getId())
                .set("updated_at", LocalDateTime.now()));
        }
    }

    /** 老师在工单上回复：插入 role=teacher 的消息，工单状态改为 HANDLING */
    public ChatMessage reply(Long ticketId, Long teacherId, String content) {
        if (!StringUtils.hasText(content)) throw new BizException("回复内容不能为空");
        HumanTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) throw new BizException(404, "工单不存在");

        ChatMessage message = appendMessage(ticket.getSessionId(), teacherId, "teacher", content, "HUMAN_HANDOFF");
        touchSession(ticket.getSessionId(), 1);
        ticketMapper.update(null, new UpdateWrapper<HumanTicket>()
            .eq("id", ticketId)
            .set("status", "HANDLING")
            .set("updated_at", LocalDateTime.now()));
        return message;
    }

    private void ensureSession(String sessionId, Long userId, String title) {
        if (chatSessionMapper.selectById(sessionId) != null) return;
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId != null ? userId : 0L);
        session.setTitle(title);
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
    }

    private ChatMessage appendMessage(String sessionId, Long userId, String role, String content, String intent) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    private void touchSession(String sessionId, int addedMessages) {
        chatSessionMapper.update(null, new UpdateWrapper<ChatSession>()
            .eq("session_id", sessionId)
            .setSql("message_count = message_count + " + addedMessages + ", updated_at = NOW()"));
    }

    private String normalizeUrgency(String urgency) {
        if (urgency == null) return "MEDIUM";
        return switch (urgency.toLowerCase()) {
            case "high", "高", "紧急" -> "HIGH";
            case "low", "低" -> "LOW";
            default -> "MEDIUM";
        };
    }
}
