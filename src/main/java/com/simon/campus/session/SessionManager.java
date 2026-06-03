package com.simon.campus.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManager {

    private static final String KEY_PREFIX = "session:";
    private static final Duration ACTIVE_TTL = Duration.ofHours(2);
    private static final Duration HUMAN_TTL  = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public SessionContext getOrCreate(String sessionId, Long userId, String username, String role) {
        if (sessionId != null) {
            Optional<SessionContext> existing = get(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        String newId = sessionId != null ? sessionId : UUID.randomUUID().toString().replace("-", "");
        SessionContext ctx = SessionContext.builder()
            .sessionId(newId)
            .userId(userId)
            .username(username)
            .role(role)
            .createdAt(LocalDateTime.now())
            .lastActiveAt(LocalDateTime.now())
            .build();
        save(ctx);
        return ctx;
    }

    public Optional<SessionContext> get(String sessionId) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, SessionContext.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize session {}: {}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(SessionContext ctx) {
        try {
            String key = KEY_PREFIX + ctx.getSessionId();
            Duration ttl = "HUMAN_PENDING".equals(ctx.getStatus()) ? HUMAN_TTL : ACTIVE_TTL;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(ctx), ttl);
        } catch (Exception e) {
            log.error("Failed to save session {}: {}", ctx.getSessionId(), e.getMessage());
        }
    }

    public void delete(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }
}
