package com.link.easyai.starter.engine.action;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.ActionContext;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.context.TaskContext;

import java.util.Map;

/**
 * Orchestrates action execution:
 * 1. Look up the main ActionExecutor by config.action.type
 * 2. Execute it with the assembled ActionContext
 * 3. If successful, execute all post-actions in order
 * <p>
 * If the main action fails, post-actions are NOT executed.
 */
public interface ActionEngine {

    /**
     * Execute the task's action.
     *
     * @param config     the task config
     * @param state      the final task state
     * @param parameters the assembled action parameters (from mapping)
     * @param taskContext the task context
     * @return action result
     */
    ActionResult execute(AiTaskConfig config,
                          TaskState state,
                          Map<String, Object> parameters,
                          TaskContext taskContext);
}
