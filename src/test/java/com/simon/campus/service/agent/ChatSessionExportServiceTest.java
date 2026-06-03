package com.simon.campus.service.agent;

import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.ChatSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionExportServiceTest {

    @Test
    void exportsSessionAsMarkdownWithImageLink() {
        ChatSession session = new ChatSession();
        session.setTitle("图片问答");
        session.setCreatedAt(LocalDateTime.of(2026, 5, 4, 10, 0));

        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("[图片] 这个图片里面是什么");
        user.setToolCalls("{\"imageUrl\":\"/api/v1/chat/images/demo.png\",\"imageName\":\"demo.png\"}");
        user.setCreatedAt(LocalDateTime.of(2026, 5, 4, 10, 1));

        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent("这是一张校园系统 ER 图。");
        assistant.setCreatedAt(LocalDateTime.of(2026, 5, 4, 10, 2));

        ChatSessionExportService service = new ChatSessionExportService();

        String markdown = service.toMarkdown(session, List.of(user, assistant));

        assertThat(markdown)
            .contains("# 图片问答")
            .contains("## 用户")
            .contains("![demo.png](/api/v1/chat/images/demo.png)")
            .contains("## 助手")
            .contains("这是一张校园系统 ER 图。");
    }
}
