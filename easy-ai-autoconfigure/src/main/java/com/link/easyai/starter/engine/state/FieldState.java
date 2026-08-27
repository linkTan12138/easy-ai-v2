package com.link.easyai.starter.engine.state;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * State of a single field within a task.
 * <p>
 * This object is persisted and restored across multiple conversation turns.
 * It tracks the raw LLM value, the validated/normalized value, and any
 * business data attached during validation (e.g. channel ID looked up from name).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FieldState {

    /** Field code */
    private String field;

    /** Current status */
    private FieldStatus status;

    /** Raw value extracted by LLM */
    private Object rawValue;

    /** Validated and normalized value */
    private Object value;

    /**
     * Human-readable display value for user-facing responses.
     * e.g. "美国" instead of "US", "单独报关" instead of 1, "DHL特快" instead of 123.
     * Populated by validators (EnumValidator, business validators) during validation.
     */
    private String displayValue;

    /** Business data attached during validation, e.g. {"id": 123, "channelName": "DHL"} */
    private Map<String, Object> data;

    /** Error code if validation failed */
    private String errorCode;

    /** Human-readable error message if validation failed */
    private String errorMessage;

    /** LLM's reasoning for extracting this value (for traceability) */
    private String extractReason;

    /** Field state version, incremented on each update (for optimistic locking) */
    @Builder.Default
    private Integer version = 0;

    /**
     * Check if this field is in a "completed" state (VALID, CONFIRMED, or SKIPPED).
     */
    @JsonIgnore
    public boolean isCompleted() {
        return status == FieldStatus.VALID
                || status == FieldStatus.CONFIRMED
                || status == FieldStatus.SKIPPED;
    }

    /**
     * Check if this field has a usable value.
     */
    @JsonIgnore
    public boolean hasValue() {
        return value != null;
    }
}
