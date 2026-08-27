package com.link.easyai.starter.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 自动意图识别聊天请求 DTO。
 * <p>
 * 与 {@link EngineChatDto} 不同，此 DTO 不需要调用方指定 taskType。
 * 框架会通过 {@link com.link.easyai.starter.engine.intent.IntentEngine}
 * 自动识别用户意图并路由到对应任务。
 * <p>
 * 调用方只需提供 sessionId（用于多轮对话状态跟踪）和用户消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoChatDto {

    /** 会话 ID，用于跟踪多轮对话中的活跃任务状态 */
    private String sessionId;

    /** 用户最新消息 */
    private String message;

    /** 租户 ID（可选，来自安全上下文） */
    private Long tenantId;
}
