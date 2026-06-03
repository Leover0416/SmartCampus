package com.simon.campus.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Suggested FAQ candidate mined from agent_logs (non-FAQ hits).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqSuggestionVO {
    private String question;
    private int askCount;
    private String intent;
    private long avgMs;
    private LocalDateTime lastAskedAt;
    /** e.g. 未命中FAQ / 知识库未召回 / 全链路较慢 */
    private String reason;
    /** Latest assistant reply as draft answer (teacher should review). */
    private String sampleAnswer;
}
