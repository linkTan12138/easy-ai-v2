package com.link.easyai.starter.engine.session;

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
 * <p>
 * 会话以 (tenant_id, session_id) 复合键唯一标识：
 * 同一 sessionId 在不同租户下各自独立（多租户多用户隔离）。
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
    public AiChatSession loadOrCreate(String sessionId, String tenantId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId cannot be blank");
        }
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "0";

        // 按复合键定位会话：同一 sessionId 在不同租户下是不同会话
        AiChatSession session = sessionMapper.selectByTenantAndSession(tenant, sessionId);
        if (session == null) {
            session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setStatus(AiChatSession.STATUS_IDLE);
            session.setTurnCount(0);
            session.setTenantId(tenant);
            session.setLastActiveTime(LocalDateTime.now());
            sessionMapper.insert(session);
            log.debug("[SessionManager] created new session: tenant={}, session={}", tenant, sessionId);
        }
        return session;
    }

    @Override
    public void bindTask(String sessionId, String taskId, String taskType, String tenantId) {
        String tenant = tenantId != null && !tenantId.isBlank() ? tenantId : "0";
        int rows = sessionMapper.bindTask(sessionId, taskId, taskType, tenant);
        if (rows == 0) {
            // Session doesn't exist yet — create it then bind
            AiChatSession session = new AiChatSession();
            session.setSessionId(sessionId);
            session.setCurrentTaskId(taskId);
            session.setCurrentTaskType(taskType);
            session.setStatus(AiChatSession.STATUS_ACTIVE);
            session.setTurnCount(0);
            session.setTenantId(tenant);
            session.setLastActiveTime(LocalDateTime.now());
            sessionMapper.insert(session);
        }
        log.info("[SessionManager] session={} (tenant={}) bound to task={} ({})", sessionId, tenant, taskId, taskType);
    }

    @Override
    public void clearTask(String sessionId, String tenantId) {
        sessionMapper.clearTask(sessionId, tenantId);
        log.debug("[SessionManager] session={} (tenant={}) cleared task", sessionId, tenantId);
    }

    @Override
    public void touch(String sessionId, String tenantId) {
        sessionMapper.touch(sessionId, tenantId);
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
    public void markExpired(String sessionId, String tenantId) {
        sessionMapper.markExpired(sessionId, tenantId);
        log.info("[SessionManager] session={} (tenant={}) marked expired", sessionId, tenantId);
    }

    @Override
    public void reset(String sessionId, String tenantId) {
        sessionMapper.reset(sessionId, tenantId);
        log.info("[SessionManager] session={} (tenant={}) reset to IDLE", sessionId, tenantId);
    }
}
