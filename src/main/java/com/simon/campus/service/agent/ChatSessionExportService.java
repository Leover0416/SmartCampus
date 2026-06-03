package com.simon.campus.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.ChatSession;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ChatSessionExportService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toMarkdown(ChatSession session, List<ChatMessage> messages) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(emptyToDefault(session.getTitle(), "聊天记录")).append("\n\n");
        if (session.getCreatedAt() != null) {
            out.append("- 创建时间：").append(session.getCreatedAt().format(TIME_FMT)).append("\n");
        }
        out.append("- 会话 ID：").append(session.getSessionId() == null ? "" : session.getSessionId()).append("\n\n");

        for (ChatMessage message : messages) {
            out.append("## ").append(roleLabel(message.getRole())).append("\n\n");
            if (message.getCreatedAt() != null) {
                out.append("> ").append(message.getCreatedAt().format(TIME_FMT)).append("\n\n");
            }
            ImageMeta image = readImageMeta(message.getToolCalls());
            if (image.imageUrl() != null) {
                out.append("![").append(emptyToDefault(image.imageName(), "上传图片")).append("](")
                    .append(image.imageUrl()).append(")\n\n");
            }
            out.append(emptyToDefault(message.getContent(), "")).append("\n\n");
        }
        return out.toString();
    }

    private ImageMeta readImageMeta(String toolCalls) {
        if (toolCalls == null || toolCalls.isBlank()) return new ImageMeta(null, null);
        try {
            var root = objectMapper.readTree(toolCalls);
            return new ImageMeta(root.path("imageUrl").asText(null), root.path("imageName").asText(null));
        } catch (Exception e) {
            return new ImageMeta(null, null);
        }
    }

    private String roleLabel(String role) {
        if ("user".equals(role)) return "用户";
        if ("assistant".equals(role)) return "助手";
        if ("teacher".equals(role)) return "老师";
        return emptyToDefault(role, "消息");
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record ImageMeta(String imageUrl, String imageName) {}
}
