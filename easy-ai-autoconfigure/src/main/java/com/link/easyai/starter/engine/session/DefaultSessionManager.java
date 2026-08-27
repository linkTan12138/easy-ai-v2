package com.link.easyai.starter.engine.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.link.easyai.starter.domain.entity.AiChatSession;
import com.link.easyai.starter.mapper.AiChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 数据库-backed 的会话状态管理器实现。
 */
@Component
public class DefaultSessionManager implements SessionManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultSessionManager.class);

    private final AiChatSessionMapper sessionMapper;

    @Autowired
    public DefaultSessionManager(AiChatSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Override
    public AiChatSession loadOrCreate(String sessionId, Long tenantId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }

        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setStatus(AiChatSession.STATUS_IDLE);
            session.setTurnCount(0);
            session.setTenantId(tenantId != null ? tenantId : 0L);
            session.setLastActiveTime(LocalDateTime.now());
            sessionMapper.insert(session);
            log.debug("[SessionManager] created new session: {}", sessionId);
        }
        return session;
    }

    @Override
    public void bindTask(String sessionId, String taskId, String taskType) {
        int rows = sessionMapper.bindTask(sessionId, taskId, taskType);
        if (rows == 0) {
            // Session doesn't exist yet — create it then bind
            AiChatSession session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setCurrentTaskId(taskId);
            session.setCurrentTaskType(taskType);
            session.setStatus(AiChatSession.STATUS_ACTIVE);
            session.setTurnCount(0);
            session.setTenantId(0L);
            session.setLastActiveTime(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        log.info("[SessionManager] session={} bound to task={} ({})", sessionId, taskId, taskType);
    }

    @Override
    public void clearTask(String sessionId) {
        sessionMapper.clearTask(sessionId);
        log.debug("[SessionManager] session={} cleared task", sessionId);
    }

    @Override
    public void touch(String sessionId) {
        sessionMapper.touch(sessionId);
    }

    @Override
    public boolean isExpired(AiChatSession session, int timeoutMinutes) {
        if (session == null || session.getLastActiveTime() == null) {
            return false;
        }
        if (session.getStatus() != null && session.getStatus() == AiChatSession.STATUS_EXPIRED) {
            return true;
        }
        Duration idle = Duration.between(session.getLastActiveTime(), LocalDateTime.now());
        return idle.toMinutes() > timeoutMinutes;
    }

    @Override
    public void markExpired(String sessionId) {
        sessionMapper.markExpired(sessionId);
        log.info("[SessionManager] session={} marked expired", sessionId);
    }

    @Override
    public void reset(String sessionId) {
        sessionMapper.reset(sessionId);
        log.info("[SessionManager] session={} reset to IDLE", sessionId);
    }
}
