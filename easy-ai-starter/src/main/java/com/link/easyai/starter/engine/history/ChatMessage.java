package com.link.easyai.starter.engine.history;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条对话消息记录。
 * <p>
 * 用于构建对话历史滑动窗口，传递给 LLM 提升多轮上下文理解能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 消息角色：user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 消息时间戳（毫秒），可选，用于排序和过期判断 */
    private Long timestamp;

    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role("user")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role("assistant")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
