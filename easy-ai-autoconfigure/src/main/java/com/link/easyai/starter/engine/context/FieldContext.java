package com.link.easyai.starter.engine.context;

import com.link.easyai.starter.engine.config.FieldDefinition;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Context for a single field during validation/normalization/mapping.
 * Provides access to the current task state, the field definition, and shared task-level data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldContext {

    /** Task ID */
    private String taskId;

    /** Task type */
    private String taskType;

    /** Config version */
    private Integer configVersion;

    /** The field code being processed */
    private String fieldCode;

    /** The field definition being processed (options, type, etc. for validators) */
    private FieldDefinition fieldDefinition;

    /** Shared task-level context data (tenant ID, user info, etc.) */
    private Map<String, Object> taskContext;

    /** All field states in the current task (read-only for validators) */
    private Map<String, Object> fieldStates;

    /**
     * Get a value from task context by key.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return taskContext != null ? (T) taskContext.get(key) : null;
    }
}
