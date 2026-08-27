package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.state.TaskState;

import java.util.List;

/**
 * Selects which fields should participate in the current extraction round.
 * <p>
 * A field participates when:
 * 1. It is not yet completed (PENDING or INVALID)
 * 2. Its premise evaluates to true (or it has no premise)
 * <p>
 * This replaces the old behavior of sending ALL field definitions to the LLM every turn.
 */
public interface FieldSelector {

    /**
     * Select pending fields for the current turn.
     *
     * @param config the task config
     * @param state  the current task state
     * @return ordered list of fields that need collection
     */
    List<FieldDefinition> select(AiTaskConfig config, TaskState state);
}
