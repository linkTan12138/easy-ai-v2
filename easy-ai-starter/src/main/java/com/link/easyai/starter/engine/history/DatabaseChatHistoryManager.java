package com.link.easyai.starter.engine.history;

import com.link.easyai.starter.domain.entity.AiChatMessage;
import com.link.easyai.starter.mapper.AiChatMessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 基于独立消息表的对话历史管理器实现。
 * <p>
 * 将每条对话消息独立存储在 ai_chat_message 表中，
 * 采用滑动窗口策略保留最近 N 条消息（默认20条，即10轮 user+assistant）。
 * 超出窗口的旧消息通过逻辑删除标记，不物理删除以便审计追溯。
 */
@Component
public class DatabaseChatHistoryManager implements ChatHistoryManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseChatHistoryManager.class);

    /** 滑动窗口最大消息数（默认20条，即10轮 user+assistant） */
    @Value("${easy-ai.task-engine.history.max-messages:20}")
    private int maxMessages;

    private final AiChatMessageMapper messageMapper;

    @Autowired
    public DatabaseChatHistoryManager(AiChatMessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<ChatMessage> loadHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 查询最近 N 条消息（SQL 按 create_time DESC，最新的在前面）
            List<AiChatMessage> recent = messageMapper.selectRecentBySessionId(sessionId, maxMessages);
            if (recent == null || recent.isEmpty()) {
                return Collections.emptyList();
            }
            List<ChatMessage> result = new ArrayList<>(recent.size());
            for (int i = recent.size() - 1; i >= 0; i--) {
                AiChatMessage m = recent.get(i);
                result.add(toChatMessage(m));
            }
            // 双重保障：按时间戳升序排序，确保历史消息是正序（最旧的在前面，最新的在后面）
            result.sort(Comparator.comparingLong(m -> m.getTimestamp() != null ? m.getTimestamp() : 0L));
            log.debug("[ChatHistory] loaded {} messages for session={}, first={}, last={}",
                    result.size(), sessionId,
                    result.isEmpty() ? "none" : result.get(0).getRole(),
                    result.isEmpty() ? "none" : result.get(result.size() - 1).getRole());
            return result;
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to load history for session={}: {}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ChatMessage> loadHistoryByTask(String sessionId, String taskId) {
        if (sessionId == null || sessionId.isBlank() || taskId == null || taskId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // 按任务ID查询，只返回该任务的消息（SQL 按 create_time ASC）
            List<AiChatMessage> taskMessages = messageMapper.selectBySessionIdAndTaskId(sessionId, taskId, maxMessages);
            if (taskMessages == null || taskMessages.isEmpty()) {
                return Collections.emptyList();
            }
            List<ChatMessage> result = new ArrayList<>(taskMessages.size());
            for (AiChatMessage m : taskMessages) {
                result.add(toChatMessage(m));
            }
            // 双重保障：按时间戳升序排序
            result.sort(Comparator.comparingLong(m -> m.getTimestamp() != null ? m.getTimestamp() : 0L));
            return result;
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to load history for session={}, task={}: {}", sessionId, taskId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void appendUserMessage(String sessionId, String content) {
        appendUserMessage(sessionId, content, null, null, null);
    }

    @Override
    public void appendAssistantMessage(String sessionId, String content) {
        appendAssistantMessage(sessionId, content, null, null, null);
    }

    @Override
    public void appendUserMessage(String sessionId, String content, String taskId, String taskType, Long tenantId) {
        appendMessage(sessionId, "user", content, taskId, taskType, tenantId);
    }

    @Override
    public void appendAssistantMessage(String sessionId, String content, String taskId, String taskType, Long tenantId) {
        appendMessage(sessionId, "assistant", content, taskId, taskType, tenantId);
    }

    private void appendMessage(String sessionId, String role, String content,
                               String taskId, String taskType, Long tenantId) {
        if (sessionId == null || sessionId.isBlank() || content == null) {
            return;
        }
        try {
            int turnIndex = messageMapper.selectMaxTurnIndex(sessionId) + 1;

            AiChatMessage message = new AiChatMessage();
            message.setSessionId(sessionId);
            message.setRole(role);
            message.setContent(content);
            message.setTaskId(taskId);
            message.setTaskType(taskType);
            message.setTurnIndex(turnIndex);
            message.setTenantId(tenantId);
            message.setDeleted(0);
            messageMapper.insert(message);

            // 滑动窗口：超出最大消息数时，逻辑删除最旧的消息
            long total = messageMapper.countBySessionId(sessionId);
            if (total > maxMessages) {
                int excess = (int) (total - maxMessages);
                List<AiChatMessage> oldMessages = messageMapper.selectRecentBySessionId(sessionId, (int) total);
                // selectRecentBySessionId 是倒序，最旧的在最后
                for (int i = 0; i < excess && i < oldMessages.size(); i++) {
                    AiChatMessage old = oldMessages.get(oldMessages.size() - 1 - i);
                    old.setDeleted(1);
                    messageMapper.updateById(old);
                }
            }

            log.debug("[ChatHistory] appended {} message to session={}, turn={}, total={}",
                    role, sessionId, turnIndex, total);
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to append message for session={}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void clearHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            messageMapper.softDeleteBySessionId(sessionId);
            log.debug("[ChatHistory] cleared history for session={}", sessionId);
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to clear history for session={}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public String formatForPrompt(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是本次对话的历史记录（仅供上下文参考，注意用户可能用\"刚才那个\"、\"上面说的\"等指代历史中的信息）：\n");
        for (ChatMessage msg : history) {
            String role = "user".equalsIgnoreCase(msg.getRole()) ? "用户" : "AI";
            sb.append("[").append(role).append("]：").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public List<AiChatMessage> listMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return messageMapper.selectAllBySessionId(sessionId);
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to list messages for session={}: {}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<AiChatMessage> listMessages(String sessionId, int page, int size) {
        if (sessionId == null || sessionId.isBlank() || page < 1 || size < 1) {
            return Collections.emptyList();
        }
        try {
            int offset = (page - 1) * size;
            return messageMapper.selectPageBySessionId(sessionId, offset, size);
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to list messages (page={},size={}) for session={}: {}",
                    page, size, sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countMessages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        try {
            return messageMapper.countBySessionId(sessionId);
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to count messages for session={}: {}", sessionId, e.getMessage());
            return 0;
        }
    }

    /**
     * 将 AiChatMessage 实体转换为 ChatMessage DTO。
     */
    private ChatMessage toChatMessage(AiChatMessage m) {
        return ChatMessage.builder()
                .role(m.getRole())
                .content(m.getContent())
                .timestamp(m.getCreateTime() != null
                        ? m.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : null)
                .build();
    }
}
