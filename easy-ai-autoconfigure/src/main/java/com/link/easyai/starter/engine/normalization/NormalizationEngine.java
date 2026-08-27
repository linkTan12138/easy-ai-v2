package com.link.easyai.starter.engine.normalization;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Orchestrates the normalization step for fields that have a NormalizationConfig.
 * <p>
 * Normalization runs AFTER validation and BEFORE mapping.
 * Fields without a NormalizationConfig skip this step (their validated value is used as-is).
 */
public interface NormalizationEngine {

    /**
     * Normalize all VALID fields that have a normalization config.
     *
     * @param state   the current task state (to be updated with normalized values)
     * @param context the task context
     */
    void normalize(TaskState state, TaskContext context);
}
