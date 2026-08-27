package com.link.easyai.starter.engine.state;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * The complete state of an AI Task, persisted across multiple conversation turns.
 * <p>
 * This is the single source of truth for "where is this task right now".
 * It must be serializable and restorable — no reliance on JVM memory between turns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskState {

    /** Task ID */
    private String taskId;

    /** Task type, e.g. "ORDER_UPDATE" */
    private String taskType;

    /** Config version this task is bound to */
    private Integer configVersion;

    /** Field states, keyed by field code */
    @Builder.Default
    private Map<String, FieldState> fields = new LinkedHashMap<>();

    /** Task status */
    private TaskStatus status;

    /** Shared context data (tenant ID, user info, business context) */
    private Map<String, Object> context;

    /**
     * Optimistic lock version, incremented on each successful save.
     * Used to detect concurrent modifications to the same task.
     */
    @Builder.Default
    private Integer version = 0;

    /**
     * Number of conversation turns processed for this task.
     * Used for max-turns lifecycle enforcement.
     */
    @Builder.Default
    private Integer turnCount = 0;

    /**
     * Get the state of a specific field.
     */
    public FieldState getField(String fieldCode) {
        return fields != null ? fields.get(fieldCode) : null;
    }

    /**
     * Update or insert a field state.
     */
    public void putField(String fieldCode, FieldState state) {
        if (fields == null) {
            fields = new LinkedHashMap<>();
        }
        fields.put(fieldCode, state);
    }

    /**
     * Check if a field is completed (VALID, CONFIRMED, or SKIPPED).
     */
    public boolean isFieldCompleted(String fieldCode) {
        FieldState fs = getField(fieldCode);
        return fs != null && fs.isCompleted();
    }

    /**
     * Check if a field exists in state (has been extracted at least once).
     */
    public boolean hasField(String fieldCode) {
        return fields != null && fields.containsKey(fieldCode);
    }

    /**
     * Get a field's validated value.
     */
    public Object getFieldValue(String fieldCode) {
        FieldState fs = getField(fieldCode);
        return fs != null ? fs.getValue() : null;
    }

    /**
     * Get a value from the shared context.
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromContext(String key) {
        return context != null ? (T) context.get(key) : null;
    }
}
