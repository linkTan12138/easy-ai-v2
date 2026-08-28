package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.task.TaskResult;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.state.TaskState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * The unified response from the AI Task Engine after processing a turn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskResponse {

    /** Task ID */
    private String taskId;

    /** Whether the task is complete (action executed) */
    private boolean completed;

    /** Message to show the user (prompt for next fields, or action result) */
    private String message;

    /** Task result if the task completed */
    private TaskResult taskResult;

    /** Current task state (for debugging / traceability) */
    private TaskState state;

    /**
     * Build a "need more info" response (task not yet complete).
     */
    public static AiTaskResponse needMore(String taskId, String message, TaskState state) {
        return AiTaskResponse.builder()
                .taskId(taskId)
                .completed(false)
                .message(message)
                .state(state)
                .build();
    }

    /**
     * Build a "task complete" response (action was executed).
     */
    public static AiTaskResponse done(String taskId, String message, TaskResult taskResult, TaskState state) {
        return AiTaskResponse.builder()
                .taskId(taskId)
                .completed(true)
                .message(message)
                .taskResult(taskResult)
                .state(state)
                .build();
    }
}
