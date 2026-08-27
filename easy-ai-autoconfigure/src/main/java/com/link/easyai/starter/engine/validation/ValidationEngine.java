package com.link.easyai.starter.engine.validation;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.extraction.ExtractionResult;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Orchestrates the validation pipeline for all extracted fields.
 * <p>
 * For each extracted field, the engine:
 * 1. Looks up the field's ValidationConfig
 * 2. Iterates through the validator pipeline
 * 3. Each validator receives the output of the previous one
 * 4. If any validator fails, the field is marked INVALID
 * 5. If all pass, the field is marked VALID with the final transformed value
 */
public interface ValidationEngine {

    /**
     * Validate all extracted fields and update task state.
     *
     * @param extraction the LLM extraction result
     * @param config     the task config (for field definitions)
     * @param state      the current task state (to be updated)
     * @param context    the task context (shared data)
     */
    void validate(ExtractionResult extraction,
                  com.link.easyai.starter.engine.config.AiTaskConfig config,
                  TaskState state,
                  com.link.easyai.starter.engine.context.TaskContext context);
}
