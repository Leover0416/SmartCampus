package com.simon.campus.model.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;
    private String query;
}
