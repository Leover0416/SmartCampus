package com.simon.campus.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.ChatSessionMapper;
import com.simon.campus.mapper.HumanTicketMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.HumanTicket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HumanHandoffService - 转人工会话")
class HumanHandoffServiceTest {

    @Test
    @DisplayName("同一会话已有未完成工单时复用工单")
    void requestHandoffReusesOpenTicket() {
        HumanTicketMapper ticketMapper = mock(HumanTicketMapper.class);
        HumanTicket existing = new HumanTicket();
        existing.setId(10L);
        existing.setSessionId("s1");
        existing.setStatus("PENDING");
        when(ticketMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        HumanHandoffService service = service(ticketMapper, mock(ChatMessageMapper.class), mock(ChatSessionMapper.class));

        HumanTicket result = service.requestHandoff("s1", 7L, "需要老师协助", "MEDIUM");

        assertThat(result).isSameAs(existing);
    }

    @Test
    @DisplayName("转人工后学生继续补充内容时只追加用户消息")
    void appendStudentMessageStoresUserMessage() {
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        HumanHandoffService service = service(mock(HumanTicketMapper.class), messageMapper, mock(ChatSessionMapper.class));

        service.appendStudentMessage("s1", 7L, "我还想补充一下");

        verify(messageMapper).insert(argThat((ChatMessage msg) ->
            "s1".equals(msg.getSessionId())
                && Long.valueOf(7L).equals(msg.getUserId())
                && "user".equals(msg.getRole())
                && "我还想补充一下".equals(msg.getContent())
                && "HUMAN_HANDOFF".equals(msg.getIntent())
        ));
    }

    @Test
    @DisplayName("教师回复写入同一会话并将工单置为处理中")
    void replyStoresTeacherMessageAndMarksHandling() {
        HumanTicketMapper ticketMapper = mock(HumanTicketMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        HumanTicket ticket = new HumanTicket();
        ticket.setId(10L);
        ticket.setSessionId("s1");
        ticket.setUserId(7L);
        ticket.setStatus("PENDING");
        when(ticketMapper.selectById(10L)).thenReturn(ticket);

        HumanHandoffService service = service(ticketMapper, messageMapper, mock(ChatSessionMapper.class));

        service.reply(10L, 99L, "请先提交申请表");

        verify(messageMapper).insert(argThat((ChatMessage msg) ->
            "s1".equals(msg.getSessionId())
                && Long.valueOf(99L).equals(msg.getUserId())
                && "teacher".equals(msg.getRole())
                && "请先提交申请表".equals(msg.getContent())
                && "HUMAN_HANDOFF".equals(msg.getIntent())
        ));
        verify(ticketMapper).update(any(), any(UpdateWrapper.class));
    }

    private HumanHandoffService service(HumanTicketMapper ticketMapper,
                                        ChatMessageMapper messageMapper,
                                        ChatSessionMapper sessionMapper) {
        return new HumanHandoffService(ticketMapper, messageMapper, sessionMapper);
    }
}
