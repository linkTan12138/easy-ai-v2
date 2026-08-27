package com.link.easyai.starter.engine.context;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.state.TaskState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Context passed to {@link com.link.easyai.starter.engine.action.ActionExecutor}
 * when a task is complete and the action is ready to execute.
 * <p>
 * Contains the assembled parameters (from field mapping), the task state,
 * and the original task context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionContext {

    /** Task ID */
    private String taskId;

    /** The task config that drove this task */
    private AiTaskConfig config;

    /** The final task state */
    private TaskState state;

    /** Assembled parameters from field mapping, e.g. {"info.receiveChannelId": 123} */
    private Map<String, Object> parameters;

    /** The original task context (tenant, user, etc.) */
    private TaskContext taskContext;

    /** Business context object (e.g. the loaded order entity) */
    private Object businessContext;
}
