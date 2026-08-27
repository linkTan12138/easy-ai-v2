package com.link.easyai.starter.engine.completion;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Determines whether a task has collected all required fields and is ready
 * for action execution.
 * <p>
 * A field is "complete" when:
 * 1. Its premise is satisfied (or it has no premise)
 * 2. If required=true: status is VALID or CONFIRMED
 * 3. If required=false: status is VALID, CONFIRMED, or SKIPPED
 * <p>
 * Important: "no value" does NOT necessarily mean "not complete" —
 * optional fields with no value can be SKIPPED and the task can still proceed.
 */
public interface CompletionEngine {

    /**
     * Check if the task is complete (all required fields collected).
     *
     * @param config the task config
     * @param state  the current task state
     * @return true if the task is ready for action execution
     */
    boolean completed(AiTaskConfig config, TaskState state);
}
