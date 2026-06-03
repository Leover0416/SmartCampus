package com.simon.campus.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {

    private String sessionId;
    private Long userId;
    private String username;
    private String role;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE / HUMAN_PENDING / CLOSED

    private String currentIntent;

    @Builder.Default
    private List<MessageRecord> history = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> slots = new HashMap<>();

    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;

    public void addMessage(String role, String content) {
        history.add(new MessageRecord(role, content, LocalDateTime.now()));
        if (history.size() > 20) {
            history.remove(0);
        }
        lastActiveAt = LocalDateTime.now();
    }

    /** Last N turns (user+assistant pairs) as history string for LLM context */
    public String buildHistoryText(int maxTurns) {
        int start = Math.max(0, history.size() - maxTurns * 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            MessageRecord msg = history.get(i);
            sb.append("user".equals(msg.role) ? "用户: " : "助手: ").append(msg.content).append("\n");
        }
        return sb.toString().strip();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageRecord {
        private String role;
        private String content;
        private LocalDateTime timestamp;
    }
}
