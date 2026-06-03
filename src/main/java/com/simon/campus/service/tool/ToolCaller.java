package com.simon.campus.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.service.admin.SystemConfigService;
import com.simon.campus.session.SessionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Tool calling orchestrator using DashScope OpenAI-compatible function calling.
 * Flow: LLM → tool_calls decision → execute tool → LLM stream final answer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolCaller {

    private final AcademicCalendarTool calendarTool;
    private final CourseSelectionTool courseSelectionTool;
    private final DepartmentContactTool departmentContactTool;
    private final HumanTicketTool humanTicketTool;
    private final SystemConfigService configService;
    private final ObjectMapper objectMapper;

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    @Value("${models.tool-caller.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // Tool definitions for DashScope function calling
    private static final List<Map<String, Object>> TOOL_DEFINITIONS = buildToolDefinitions();

    public static final String DEFAULT_ACADEMIC_SYSTEM_PROMPT =
        "你是SmartCampus校园智能助手，擅长查询教务信息。" +
        "请根据用户需求选择合适的工具获取准确信息，并以友好清晰的方式回答。" +
        "当前时间：2026年春季学期（2025-2026-2）。";

    public record ToolCallResult(
        String answer,
        ToolResult toolResult
    ) {}

    public ToolCallResult call(String query, SessionContext session, Consumer<String> onToken) {
        try {
            // Check if tools are enabled
            if (!configService.getBool("tool.query_academic_calendar.enabled", true) &&
                !configService.getBool("tool.query_course_selection.enabled", true) &&
                !configService.getBool("tool.query_department_contact.enabled", true)) {
                return fallbackToText(query, onToken);
            }

            List<Map<String, Object>> messages = buildMessages(query, session);
            String modelToUse = configService.get("models.tool-caller.model", model);
            log.info("[MODEL_CALL] type=tool_decision model={} messages={}",
                modelToUse, abbreviate(String.valueOf(messages)));

            // Step 1: Ask LLM which tool to call
            long decisionStart = System.currentTimeMillis();
            Map<String, Object> body = buildBody(modelToUse, messages, false);
            HttpRequest request = buildRequest(body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("ToolCaller step1 failed [{}]: {}", response.statusCode(), response.body());
                return fallbackToText(query, onToken);
            }

            JsonNode root = objectMapper.readTree(response.body());
            log.info("[MODEL_RETURN] type=tool_decision model={} costMs={} response={}",
                modelToUse, System.currentTimeMillis() - decisionStart, abbreviate(response.body()));
            JsonNode choice = root.path("choices").get(0);
            JsonNode message = choice.path("message");
            JsonNode toolCalls = message.path("tool_calls");

            // If no tool call, treat as regular answer
            if (toolCalls.isMissingNode() || toolCalls.isEmpty()) {
                String directAnswer = message.path("content").asText("");
                onToken.accept(directAnswer);
                return new ToolCallResult(directAnswer, null);
            }

            // Step 2: Execute the tool
            JsonNode firstCall = toolCalls.get(0);
            String callId = firstCall.path("id").asText();
            String funcName = firstCall.path("function").path("name").asText();
            String argsJson  = firstCall.path("function").path("arguments").asText("{}");

            log.info("Tool call: {} args={}", funcName, argsJson);
            ToolResult toolResult = executeTool(funcName, argsJson, session);
            log.info("[TOOL_FLOW] tool={} args={} result={}",
                funcName, abbreviate(argsJson), abbreviate(objectMapper.writeValueAsString(toolResult)));

            // Step 3: Build messages with tool result, stream final answer
            messages = new ArrayList<>(messages);
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", "");
            assistantMsg.put("tool_calls", List.of(Map.of(
                "id", callId,
                "type", "function",
                "function", Map.of("name", funcName, "arguments", argsJson)
            )));
            messages.add(assistantMsg);

            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", callId);
            toolMsg.put("content", objectMapper.writeValueAsString(toolResult.getData()));
            messages.add(toolMsg);

            // Step 4: Stream final answer
            String finalAnswer = streamFinalAnswer(modelToUse, messages, onToken);
            return new ToolCallResult(finalAnswer, toolResult);

        } catch (Exception e) {
            log.error("ToolCaller failed: {}", e.getMessage(), e);
            return fallbackToText(query, onToken);
        }
    }

    private ToolResult executeTool(String name, String argsJson, SessionContext session) throws Exception {
        JsonNode args = objectMapper.readTree(argsJson);
        return switch (name) {
            case "query_academic_calendar" -> {
                String term = args.path("term").asText(null);
                yield calendarTool.query(term);
            }
            case "query_course_selection" -> {
                String term = args.path("term").asText(null);
                yield courseSelectionTool.query(term);
            }
            case "query_department_contact" -> {
                String dept = args.path("department").asText(null);
                yield departmentContactTool.query(dept);
            }
            case "create_human_ticket" -> {
                String summary  = args.path("summary").asText(null);
                String urgency  = args.path("urgency").asText("MEDIUM");
                String sessId   = session != null ? session.getSessionId() : null;
                Long userId     = session != null ? session.getUserId() : null;
                yield humanTicketTool.create(sessId, userId, summary, urgency);
            }
            default -> ToolResult.builder().success(false).toolName(name)
                .error("Unknown tool: " + name).build();
        };
    }

    private String streamFinalAnswer(String modelName, List<Map<String, Object>> messages,
                                      Consumer<String> onToken) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[MODEL_CALL] type=tool_final_stream model={} messages={}",
            modelName, abbreviate(String.valueOf(messages)));
        Map<String, Object> body = buildBody(modelName, messages, true);
        HttpRequest request = buildRequest(body);
        HttpResponse<java.io.InputStream> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String err = new String(response.body().readAllBytes());
            throw new RuntimeException("Stream failed [" + response.statusCode() + "]: " + err);
        }

        StringBuilder full = new StringBuilder();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonNode r = objectMapper.readTree(data);
                    String token = r.path("choices").get(0).path("delta").path("content").asText("");
                    if (!token.isEmpty()) { full.append(token); onToken.accept(token); }
                } catch (Exception ignored) {}
            }
        }
        log.info("[MODEL_RETURN] type=tool_final_stream model={} costMs={} response={}",
            modelName, System.currentTimeMillis() - start, abbreviate(full.toString()));
        return full.toString();
    }

    private ToolCallResult fallbackToText(String query, Consumer<String> onToken) {
        String msg = "抱歉，工具服务暂时不可用，请稍后重试或直接联系相关部门。";
        onToken.accept(msg);
        return new ToolCallResult(msg, null);
    }

    private List<Map<String, Object>> buildMessages(String query, SessionContext session) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content",
            configService.get("prompt.academic_default", DEFAULT_ACADEMIC_SYSTEM_PROMPT)));
        String history = session != null ? session.buildHistoryText(3) : "";
        String content = history.isBlank() ? query : history + "\n用户: " + query;
        msgs.add(Map.of("role", "user", "content", content));
        return msgs;
    }

    private Map<String, Object> buildBody(String modelName, List<Map<String, Object>> messages,
                                           boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("tools", getEnabledTools());
        body.put("tool_choice", "auto");
        body.put("temperature", configService.getDouble("models.tool-caller.temperature", 0.1));
        body.put("max_tokens", configService.getInt("models.tool-caller.max-tokens", 1024));
        if (stream) body.put("stream", true);
        return body;
    }

    private List<Map<String, Object>> getEnabledTools() {
        List<Map<String, Object>> enabled = new ArrayList<>();
        for (Map<String, Object> tool : TOOL_DEFINITIONS) {
            String name = (String) ((Map<?,?>) tool.get("function")).get("name");
            String key = "tool." + name + ".enabled";
            if (configService.getBool(key, true)) enabled.add(tool);
        }
        return enabled.isEmpty() ? TOOL_DEFINITIONS : enabled;
    }

    private HttpRequest buildRequest(Map<String, Object> body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(90))
            .build();
    }

    private static List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
            tool("query_academic_calendar", "查询校历安排，包括开学放假考试等事件",
                Map.of("term", param("string", "学期，格式如 2025-2026-2，不填则查当前学期")),
                List.of()),
            tool("query_course_selection", "查询选课安排，包括各轮选课和退课时间",
                Map.of("term", param("string", "学期，格式如 2025-2026-2，不填则查当前学期")),
                List.of()),
            tool("query_department_contact", "查询院系或行政部门联系方式",
                Map.of("department", param("string", "部门名称，如教务处、计算机学院，不填则返回所有")),
                List.of()),
            tool("create_human_ticket", "创建人工客服工单，转接给老师处理",
                Map.of(
                    "summary",  param("string", "问题摘要，描述用户的问题或诉求"),
                    "urgency",  param("string", "紧急程度：HIGH/MEDIUM/LOW")
                ),
                List.of("summary"))
        );
    }

    private static Map<String, Object> tool(String name, String description,
                                             Map<String, Map<String, Object>> properties,
                                             List<String> required) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        if (!required.isEmpty()) params.put("required", required);
        return Map.of(
            "type", "function",
            "function", Map.of("name", name, "description", description, "parameters", params)
        );
    }

    private static Map<String, Object> param(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 4000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
