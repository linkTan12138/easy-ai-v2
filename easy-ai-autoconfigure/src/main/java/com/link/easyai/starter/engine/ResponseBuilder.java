package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Builds user-facing response messages from task state.
 * <p>
 * When the task is NOT complete, builds a message listing:
 * - Already collected fields
 - Fields still needed (with examples / error messages)
 * <p>
 * When the task IS complete, builds a message from the ActionResult.
 */
public interface ResponseBuilder {

    /**
     * Build a response for when more fields are needed.
     *
     * @param config the task config
     * @param state  the current task state
     * @return user-facing message
     */
    String buildNeedMore(AiTaskConfig config, TaskState state);

    /**
     * Build a response for when the task is complete (action executed).
     *
     * @param actionResult the action result
     * @return user-facing message
     */
    String buildDone(com.link.easyai.starter.engine.action.ActionResult actionResult);
}
