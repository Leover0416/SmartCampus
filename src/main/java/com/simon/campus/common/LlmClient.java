package com.simon.campus.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * DashScope OpenAI-compatible endpoint client.
 * Supports both blocking and streaming (SSE) chat completions.
 */
@Component
@Slf4j
public class LlmClient {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.base-url}")
    private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Blocking chat completion ──────────────────────────────────────────────

    public String chat(String model, double temperature, int maxTokens,
                       List<Map<String, String>> messages) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[MODEL_CALL] type=chat model={} temperature={} maxTokens={} messages={}",
            model, temperature, maxTokens, summarize(messages));
        Map<String, Object> body = buildBody(model, temperature, maxTokens, messages, false);
        HttpRequest request = buildRequest(body);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM call failed [" + response.statusCode() + "]: " + response.body());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        log.info("[MODEL_RETURN] type=chat model={} costMs={} response={}",
            model, System.currentTimeMillis() - start, abbreviate(content));
        return content;
    }

    // ── Streaming chat completion ─────────────────────────────────────────────

    /**
     * Streams tokens from DashScope SSE into onToken callback.
     * onDone is called with the full accumulated content when stream ends.
     */
    public String chatStream(String model, double temperature, int maxTokens,
                              List<Map<String, String>> messages,
                              Consumer<String> onToken) throws Exception {
        long start = System.currentTimeMillis();
        log.info("[MODEL_CALL] type=stream model={} temperature={} maxTokens={} messages={}",
            model, temperature, maxTokens, summarize(messages));
        Map<String, Object> body = buildBody(model, temperature, maxTokens, messages, true);
        HttpRequest request = buildRequest(body);

        HttpResponse<java.io.InputStream> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String err = new String(response.body().readAllBytes());
            throw new RuntimeException("LLM stream failed [" + response.statusCode() + "]: " + err);
        }

        StringBuilder fullContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if ("[DONE]".equals(data)) break;
                try {
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode delta = root.path("choices").get(0).path("delta");
                    String token = delta.path("content").asText("");
                    if (!token.isEmpty()) {
                        fullContent.append(token);
                        onToken.accept(token);
                    }
                } catch (Exception e) {
                    log.debug("Skip unparseable SSE line: {}", data);
                }
            }
        }
        log.info("[MODEL_RETURN] type=stream model={} costMs={} response={}",
            model, System.currentTimeMillis() - start, abbreviate(fullContent.toString()));
        return fullContent.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildBody(String model, double temperature, int maxTokens,
                                           List<Map<String, String>> messages, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (stream) body.put("stream", true);
        return body;
    }

    private HttpRequest buildRequest(Map<String, Object> body) throws Exception {
        return HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .timeout(Duration.ofSeconds(60))
            .build();
    }

    /** Convert a Msg list to the Map format used by chat/chatStream. */
    public static List<Map<String, String>> toMaps(List<Msg> messages) {
        List<Map<String, String>> raw = new ArrayList<>();
        for (Msg m : messages) raw.add(Map.of("role", m.role(), "content", m.content()));
        return raw;
    }

    // ── Message builder helpers ───────────────────────────────────────────────

    public record Msg(String role, String content) {
        public static Msg system(String content) { return new Msg("system", content); }
        public static Msg user(String content)   { return new Msg("user", content); }
        public static Msg assistant(String c)    { return new Msg("assistant", c); }
    }

    public static Map<String, String> systemMsg(String content) {
        return Map.of("role", "system", "content", content);
    }

    public static Map<String, String> userMsg(String content) {
        return Map.of("role", "user", "content", content);
    }

    public static Map<String, String> assistantMsg(String content) {
        return Map.of("role", "assistant", "content", content);
    }

    private String summarize(Object value) {
        return abbreviate(String.valueOf(value));
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").strip();
        int max = 4000;
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...[truncated]";
    }
}
