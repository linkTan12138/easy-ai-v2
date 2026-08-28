package com.link.easyai.starter.engine.context;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Task-level context shared across the entire task lifecycle.
 * Carries tenant info, user details, and any cross-field shared data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskContext {

    /** Task ID */
    private String taskId;

    /** Session ID (for loading conversation history) */
    private String sessionId;

    /** Task type */
    private String taskType;

    /** Config version */
    private Integer configVersion;

    /** Tenant ID */
    private Long tenantId;

    /** User details (from security context or session) */
    private Object userDetails;

    /** Shared mutable context data across fields */
    private Map<String, Object> data;

    /**
     * Put a value into the shared data map.
     */
    public void put(String key, Object value) {
        if (data != null) {
            data.put(key, value);
        }
    }

    /**
     * Get a value from the shared data map.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return data != null ? (T) data.get(key) : null;
    }
}
