package com.simon.campus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simon.campus.common.BizException;
import com.simon.campus.common.R;
import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.ChatSessionMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.ChatSession;
import com.simon.campus.model.entity.HumanTicket;
import com.simon.campus.model.vo.ChatMessageVO;
import com.simon.campus.model.vo.SessionVO;
import com.simon.campus.service.admin.HumanHandoffService;
import com.simon.campus.service.agent.AgentOrchestrator;
import com.simon.campus.service.agent.ChatImageStorageService;
import com.simon.campus.service.agent.ChatSessionExportService;
import com.simon.campus.service.agent.VisionQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 【导读】学生端对话 API。
 * <ul>
 *   <li>GET /stream：SSE 流式问答（EventSource），结束后 persistMessages 落库</li>
 *   <li>POST /handoff：点击「转人工」创建工单并写入消息</li>
 *   <li>若已有 OPEN 工单，后续消息走 appendStudentMessage，不再走完整 RAG</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AgentOrchestrator orchestrator;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final HumanHandoffService handoffService;
    private final ChatImageStorageService chatImageStorageService;
    private final ChatSessionExportService chatSessionExportService;
    private final VisionQuestionService visionQuestionService;
    private final ObjectMapper objectMapper;

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    // ── SSE streaming endpoint ────────────────────────────────────────────────

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String query,
            @RequestParam(required = false) String sessionId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getCredentials() instanceof Long)) throw new BizException(401, "未授权，请提供有效 token");
        UserInfo user = new UserInfo(
            (Long) auth.getCredentials(),
            (String) auth.getPrincipal(),
            auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("STUDENT")
        );
        String sid = resolveSessionId(sessionId, user.userId());

        SseEmitter emitter = new SseEmitter(120_000L);

        sseExecutor.submit(() -> {
            try {
                HumanTicket openTicket = handoffService.findOpenTicket(sid);
                if (openTicket != null) {
                    handoffService.appendStudentMessage(sid, user.userId(), query);
                    String ack = "你的补充内容已同步给老师，老师会在此会话中回复。";
                    emitter.send(SseEmitter.event()
                        .name("token")
                        .data(Map.of("token", ack), MediaType.APPLICATION_JSON));
                    Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
                    donePayload.put("sessionId", sid);
                    donePayload.put("intent", "HUMAN_HANDOFF");
                    donePayload.put("handoff", true);
                    donePayload.put("ticketId", openTicket.getId());
                    emitter.send(SseEmitter.event()
                        .name("done")
                        .data(donePayload, MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                AgentOrchestrator.OrchestrationResult result = orchestrator.handleStream(
                    query, sid, user.userId(), user.username(), user.role(),
                    token -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("token")
                                .data(Map.of("token", token), MediaType.APPLICATION_JSON));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                );

                // Persist messages
                persistMessages(sid, user.userId(), query, result);

                // Send done event with metadata
                Map<String, Object> donePayload = new java.util.LinkedHashMap<>();
                donePayload.put("sessionId", sid);
                donePayload.put("intent", result.getIntent());
                if (result.getToolResult() != null) {
                    donePayload.put("toolResult", result.getToolResult());
                }
                donePayload.put("sourceRefs", result.getSourceRefs());
                emitter.send(SseEmitter.event()
                    .name("done")
                    .data(donePayload, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error for session {}: {}", sid, e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "处理请求时出现错误"), MediaType.APPLICATION_JSON));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Map<String, Object>> imageQuestion(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sessionId,
            @RequestPart("image") MultipartFile image) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getCredentials() instanceof Long)) throw new BizException(401, "未授权，请提供有效 token");
        UserInfo user = new UserInfo(
            (Long) auth.getCredentials(),
            (String) auth.getPrincipal(),
            auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("STUDENT")
        );
        if (image == null || image.isEmpty()) throw new BizException("请上传图片");
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BizException("仅支持上传图片文件");
        }
        if (image.getSize() > 8L * 1024 * 1024) {
            throw new BizException("图片不能超过 8MB");
        }

        String sid = resolveSessionId(sessionId, user.userId());
        String displayQuery = (query == null || query.isBlank()) ? "请分析这张图片" : query.strip();
        ChatImageStorageService.StoredImage storedImage = chatImageStorageService.save(
            image.getBytes(), contentType, image.getOriginalFilename()
        );
        String augmentedQuery = visionQuestionService.buildImageQuestion(displayQuery, image.getBytes(), contentType);

        AgentOrchestrator.OrchestrationResult result = orchestrator.handleStream(
            augmentedQuery, sid, user.userId(), user.username(), user.role(), token -> {}
        );
        persistMessages(sid, user.userId(), "[图片] " + displayQuery, result, storedImage);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("sessionId", sid);
        payload.put("answer", result.getAnswer());
        payload.put("intent", result.getIntent());
        payload.put("imageUrl", storedImage.url());
        payload.put("imageName", storedImage.originalName());
        payload.put("sourceRefs", result.getSourceRefs());
        if (result.getToolResult() != null) {
            payload.put("toolResult", result.getToolResult());
        }
        return R.ok(payload);
    }

    @GetMapping("/images/{fileName:.+}")
    public ResponseEntity<byte[]> getChatImage(@PathVariable String fileName) throws Exception {
        ChatImageStorageService.ImageResource image = chatImageStorageService.load(fileName);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType()))
            .body(image.bytes());
    }

    // ── Session management ────────────────────────────────────────────────────

    @GetMapping("/sessions")
    public R<List<SessionVO>> listSessions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (Long) auth.getCredentials();
        LambdaQueryWrapper<ChatSession> qw = new LambdaQueryWrapper<ChatSession>()
            .eq(ChatSession::getUserId, userId)
            .orderByDesc(ChatSession::getUpdatedAt);
        List<SessionVO> sessions = chatSessionMapper.selectList(qw).stream()
            .map(s -> SessionVO.builder()
                .sessionId(s.getSessionId())
                .title(s.getTitle())
                .status(s.getStatus())
                .messageCount(s.getMessageCount())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build())
            .toList();
        return R.ok(sessions);
    }

    @PostMapping("/handoff")
    public R<Map<String, Object>> requestHandoff(@RequestBody HandoffRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getCredentials() instanceof Long)) throw new BizException(401, "未授权，请提供有效 token");
        Long userId = (Long) auth.getCredentials();
        String sid = resolveSessionId(request.sessionId(), userId);
        HumanTicket ticket = handoffService.requestHandoff(sid, userId, request.summary(), request.urgency());
        return R.ok(Map.of(
            "sessionId", sid,
            "ticketId", ticket.getId(),
            "ticketNo", ticket.getTicketNo(),
            "status", ticket.getStatus()
        ));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatMessageVO>> getMessages(@PathVariable String sessionId) {
        Long userId = currentUserId();
        requireOwnedSession(sessionId, userId);
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getSessionId, sessionId)
            .orderByAsc(ChatMessage::getId);
        List<ChatMessageVO> msgs = chatMessageMapper.selectList(qw).stream()
            .map(m -> ChatMessageVO.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .intent(m.getIntent())
                .imageUrl(readImageMeta(m).imageUrl())
                .imageName(readImageMeta(m).imageName())
                .sourceRefs(readSourceRefs(m.getSourceRefs()))
                .createdAt(m.getCreatedAt())
                .build())
            .toList();
        return R.ok(msgs);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> deleteSession(@PathVariable String sessionId) {
        Long userId = currentUserId();
        requireOwnedSession(sessionId, userId);
        chatSessionMapper.deleteById(sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getSessionId, sessionId));
        return R.ok(null);
    }

    @GetMapping(value = "/sessions/{sessionId}/export", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportSession(@PathVariable String sessionId) {
        Long userId = currentUserId();
        ChatSession session = requireOwnedSession(sessionId, userId);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
            .eq(ChatMessage::getSessionId, sessionId)
            .orderByAsc(ChatMessage::getId));
        String markdown = chatSessionExportService.toMarkdown(session, messages);
        String fileName = sanitizeFileName(session.getTitle()) + ".md";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
            .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
            .body(markdown);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void persistMessages(String sessionId, Long userId, String query,
                                  AgentOrchestrator.OrchestrationResult result) {
        persistMessages(sessionId, userId, query, result, null);
    }

    private void persistMessages(String sessionId, Long userId, String query,
                                  AgentOrchestrator.OrchestrationResult result,
                                  ChatImageStorageService.StoredImage image) {
        try {
            // Ensure session record exists
            ChatSession existing = chatSessionMapper.selectById(sessionId);
            if (existing == null) {
                ChatSession session = new ChatSession();
                session.setSessionId(sessionId);
                session.setUserId(userId);
                session.setTitle(query.length() > 30 ? query.substring(0, 30) + "..." : query);
                session.setStatus("ACTIVE");
                session.setMessageCount(0);
                session.setCreatedAt(LocalDateTime.now());
                session.setUpdatedAt(LocalDateTime.now());
                chatSessionMapper.insert(session);
            }

            // User message
            ChatMessage userMsg = new ChatMessage();
            userMsg.setSessionId(sessionId);
            userMsg.setUserId(userId);
            userMsg.setRole("user");
            userMsg.setContent(query);
            userMsg.setIntent(result.getIntent());
            if (image != null) {
                userMsg.setToolCalls(objectMapper.writeValueAsString(Map.of(
                    "imageUrl", image.url(),
                    "imageName", image.originalName() == null ? "" : image.originalName()
                )));
            }
            userMsg.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(userMsg);

            // Assistant message
            ChatMessage assistantMsg = new ChatMessage();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setUserId(userId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(result.getAnswer());
            assistantMsg.setIntent(result.getIntent());
            if (!result.getSourceRefs().isEmpty()) {
                assistantMsg.setSourceRefs(objectMapper.writeValueAsString(result.getSourceRefs()));
            }
            assistantMsg.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(assistantMsg);

            // Update session message count
            chatSessionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSession>()
                    .eq(ChatSession::getSessionId, sessionId)
                    .setSql("message_count = message_count + 2, updated_at = NOW()"));
        } catch (Exception e) {
            log.warn("Failed to persist messages: {}", e.getMessage());
        }
    }

    private List<ChatMessageVO.SourceRefVO> readSourceRefs(String sourceRefs) {
        if (sourceRefs == null || sourceRefs.isBlank()) {
            return List.of();
        }
        try {
            var root = objectMapper.readTree(sourceRefs);
            if (!root.isArray()) {
                return List.of();
            }
            List<ChatMessageVO.SourceRefVO> refs = new java.util.ArrayList<>();
            for (var item : root) {
                refs.add(ChatMessageVO.SourceRefVO.builder()
                    .docTitle(item.path("docTitle").asText(""))
                    .headingPath(item.path("headingPath").asText(null))
                    .pageStart(item.path("pageStart").isMissingNode() || item.path("pageStart").isNull()
                        ? null : item.path("pageStart").asInt())
                    .build());
            }
            return refs;
        } catch (Exception e) {
            log.warn("Failed to parse source refs: {}", e.getMessage());
            return List.of();
        }
    }

    private ImageMeta readImageMeta(ChatMessage message) {
        if (message.getToolCalls() == null || message.getToolCalls().isBlank()) {
            return new ImageMeta(null, null);
        }
        try {
            var root = objectMapper.readTree(message.getToolCalls());
            return new ImageMeta(
                root.path("imageUrl").asText(null),
                root.path("imageName").asText(null)
            );
        } catch (Exception e) {
            return new ImageMeta(null, null);
        }
    }

    private String resolveSessionId(String sessionId, Long userId) {
        if (sessionId != null && !sessionId.isBlank()) return sessionId;
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth.getCredentials() instanceof Long userId)) throw new BizException(401, "未授权，请提供有效 token");
        return userId;
    }

    private ChatSession requireOwnedSession(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) throw new BizException(404, "会话不存在");
        if (!userId.equals(session.getUserId())) throw new BizException(403, "无权访问该会话");
        return session;
    }

    private String sanitizeFileName(String title) {
        String value = title == null || title.isBlank() ? "聊天记录" : title;
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
    }

    private record UserInfo(Long userId, String username, String role) {}
    private record HandoffRequest(String sessionId, String summary, String urgency) {}
    private record ImageMeta(String imageUrl, String imageName) {}
}
