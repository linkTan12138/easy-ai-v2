package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.context.TaskContext;

/**
 * Unified high-level entry point for AI chat conversations.
 * <p>
 * Unlike {@link AiTaskEngine} which requires the caller to specify a taskType,
 * this service automatically recognizes the user's intent and routes to the
 * appropriate task. Callers only need to provide the message and session ID.
 * <p>
 * Usage:
 * <pre>
 * ChatResponse resp = aiChatService.chat(userMessage, sessionId, taskContext);
 * </pre>
 * <p>
 * The response contains the user-facing message plus task metadata
 * (taskId, taskType, completion status, current state).
 */
public interface AiChatService {

    /**
     * Process a user message with automatic intent recognition.
     *
     * @param message   the user's message text
     * @param sessionId the conversation session ID (used to track ongoing tasks)
     * @param context   optional task context (tenant ID, user details, business data)
     * @return the chat response with message and task metadata
     */
    ChatResponse chat(String message, String sessionId, TaskContext context);

    /**
     * Process a user message with a pre-determined task type (bypasses intent recognition).
     * Useful when the caller already knows the task type (e.g. from a button click).
     *
     * @param taskType  the task type to execute
     * @param message   the user's message text
     * @param sessionId the conversation session ID
     * @param context   optional task context
     * @return the chat response
     */
    ChatResponse chatWithTaskType(String taskType, String message, String sessionId, TaskContext context);
}
