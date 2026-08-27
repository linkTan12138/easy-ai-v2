package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.state.TaskState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Response from the unified {@link AiChatService} entry point.
 * <p>
 * Carries the user-facing message plus metadata about the task state,
 * so callers can track progress, render collected fields, or detect completion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** The user-facing reply message. */
    private String message;

    /** The task ID (may be null if no task was matched/created). */
    private String taskId;

    /** The matched task type (may be null for fallback/ambiguous responses). */
    private String taskType;

    /** Whether the task has completed (action executed successfully). */
    private boolean completed;

    /** Whether the user needs to provide more information. */
    private boolean needMore;

    /** Whether the response is a clarification request (low intent confidence). */
    private boolean clarification;

    /** The current task state (for progress rendering, may be null). */
    private TaskState taskState;

    /** The action result if the task completed (may be null). */
    private ActionResult actionResult;

    /**
     * Create a "need more info" response.
     */
    public static ChatResponse needMore(String taskId, String message, TaskState state) {
        return ChatResponse.builder()
                .message(message)
                .taskId(taskId)
                .taskType(state != null ? state.getTaskType() : null)
                .needMore(true)
                .completed(false)
                .taskState(state)
                .build();
    }

    /**
     * Create a "task completed" response.
     */
    public static ChatResponse done(String taskId, String message, ActionResult result, TaskState state) {
        return ChatResponse.builder()
                .message(message)
                .taskId(taskId)
                .taskType(state != null ? state.getTaskType() : null)
                .needMore(false)
                .completed(true)
                .taskState(state)
                .actionResult(result)
                .build();
    }

    /**
     * Create a clarification response (low intent confidence).
     */
    public static ChatResponse clarify(String message) {
        return ChatResponse.builder()
                .message(message)
                .clarification(true)
                .needMore(true)
                .build();
    }

    /**
     * Create a clarification response with candidate options.
     */
    public static ChatResponse clarify(String message, java.util.List<String> candidates, String reason) {
        StringBuilder sb = new StringBuilder(message);
        if (candidates != null && !candidates.isEmpty()) {
            sb.append("\n可选操作：").append(String.join("、", candidates));
        }
        return ChatResponse.builder()
                .message(sb.toString())
                .clarification(true)
                .needMore(true)
                .build();
    }

    /**
     * Create a fallback response (no intent matched).
     */
    public static ChatResponse fallback(String message) {
        return ChatResponse.builder()
                .message(message)
                .build();
    }

    /**
     * Chainable setter for taskType.
     */
    public ChatResponse withTaskType(String taskType) {
        this.taskType = taskType;
        return this;
    }
}
