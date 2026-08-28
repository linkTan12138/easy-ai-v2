package com.link.easyai.starter.engine.history;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.AiChatSession;
import com.link.easyai.starter.mapper.AiChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于数据库的对话历史管理器实现。
 * <p>
 * 将对话历史以 JSON 数组形式存储在 ai_chat_session.chat_history 字段中，
 * 采用滑动窗口策略保留最近 N 轮对话（默认10轮，即20条消息）。
 */
@Component
public class DatabaseChatHistoryManager implements ChatHistoryManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseChatHistoryManager.class);

    /** 滑动窗口最大消息数（默认20条，即10轮 user+assistant） */
    @Value("${easy-ai.task-engine.history.max-messages:20}")
    private int maxMessages;

    private final AiChatSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public DatabaseChatHistoryManager(AiChatSessionMapper sessionMapper, ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChatMessage> loadHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getChatHistory() == null || session.getChatHistory().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(session.getChatHistory(),
                    new TypeReference<List<ChatMessage>>() {});
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to parse chat history for session={}: {}", sessionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void appendUserMessage(String sessionId, String content) {
        appendMessage(sessionId, ChatMessage.user(content));
    }

    @Override
    public void appendAssistantMessage(String sessionId, String content) {
        appendMessage(sessionId, ChatMessage.assistant(content));
    }

    private void appendMessage(String sessionId, ChatMessage message) {
        if (sessionId == null || sessionId.isBlank() || message == null) {
            return;
        }
        try {
            List<ChatMessage> history = loadHistory(sessionId);
            if (history == null) {
                history = new ArrayList<>();
            }
            history.add(message);

            // 滑动窗口：超出最大消息数时丢弃最旧的消息
            while (history.size() > maxMessages) {
                history.remove(0);
            }

            String json = objectMapper.writeValueAsString(history);
            sessionMapper.updateChatHistory(sessionId, json);
            log.debug("[ChatHistory] appended message to session={}, total={}", sessionId, history.size());
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
            sessionMapper.clearChatHistory(sessionId);
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
        sb.append("以下是本次对话的历史记录（仅供上下文参考，不要重复回答已回答过的问题）：\n");
        for (ChatMessage msg : history) {
            String role = "user".equalsIgnoreCase(msg.getRole()) ? "用户" : "AI";
            sb.append("[").append(role).append("]：").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }
}
