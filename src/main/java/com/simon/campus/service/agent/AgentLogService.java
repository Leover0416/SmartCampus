package com.simon.campus.service.agent;

import com.simon.campus.mapper.AgentLogMapper;
import com.simon.campus.model.entity.AgentLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentLogService {

    private final AgentLogMapper agentLogMapper;

    @Async
    public void save(AgentLog log) {
        try {
            log.setCreatedAt(LocalDateTime.now());
            agentLogMapper.insert(log);
        } catch (Exception e) {
            AgentLogService.log.warn("Failed to save agent log: {}", e.getMessage());
        }
    }
}
