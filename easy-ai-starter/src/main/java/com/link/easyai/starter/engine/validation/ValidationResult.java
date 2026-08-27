package com.link.easyai.starter.engine.validation;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Result of a single validator or the entire validation pipeline for a field.
 * <p>
 * - rawValue: the original LLM-extracted value
 * - value: the validated and potentially transformed value (standard value)
 * - displayValue: human-readable value for user-facing responses (e.g. "美国" not "US")
 * - data: business data attached during validation (e.g. channel ID, channel name)
 * - message: error message if validation failed
 * - errorCode: machine-readable error code
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /** Whether validation passed */
    private boolean valid;

    /** Original raw value from LLM */
    private Object rawValue;

    /** Validated/transformed value (standard value) */
    private Object value;

    /**
     * Human-readable display value for user-facing responses.
     * Populated by validators when they can resolve a label/name for the value.
     */
    private String displayValue;

    /** Business data attached during validation */
    private Map<String, Object> data;

    /** Human-readable error message */
    private String message;

    /** Machine-readable error code */
    private String errorCode;

    /**
     * Create a success result with raw, standard, and display values.
     */
    public static ValidationResult success(Object rawValue, Object value, String displayValue, Map<String, Object> data) {
        return ValidationResult.builder()
                .valid(true)
                .rawValue(rawValue)
                .value(value)
                .displayValue(displayValue)
                .data(data)
                .build();
    }

    /**
     * Create a success result with raw and standard values (no display value).
     */
    public static ValidationResult success(Object rawValue, Object value, Map<String, Object> data) {
        return ValidationResult.builder()
                .valid(true)
                .rawValue(rawValue)
                .value(value)
                .data(data)
                .build();
    }

    /**
     * Create a simple success result (value unchanged, no display value).
     */
    public static ValidationResult success(Object value) {
        return ValidationResult.builder()
                .valid(true)
                .rawValue(value)
                .value(value)
                .build();
    }

    /**
     * Create a failure result.
     */
    public static ValidationResult fail(Object rawValue, String errorCode, String message) {
        return ValidationResult.builder()
                .valid(false)
                .rawValue(rawValue)
                .errorCode(errorCode)
                .message(message)
                .build();
    }
}
