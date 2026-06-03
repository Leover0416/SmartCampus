package com.simon.campus.service.tool;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ToolResult {
    private boolean success;
    private String toolName;
    private Map<String, Object> params;
    private Object data;
    private String summary;
    private String dataSource;
    private String updatedAt;
    private String error;
}
