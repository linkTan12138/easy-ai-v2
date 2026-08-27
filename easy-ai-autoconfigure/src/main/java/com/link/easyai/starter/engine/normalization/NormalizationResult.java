package com.link.easyai.starter.engine.normalization;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Result of field normalization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizationResult {

    /** Whether normalization succeeded */
    private boolean success;

    /** Normalized standard value */
    private Object value;

    /** Business data attached during normalization */
    private Map<String, Object> data;

    /** Error message if normalization failed */
    private String errorMessage;

    /**
     * Create a success result.
     */
    public static NormalizationResult success(Object value, Map<String, Object> data) {
        return NormalizationResult.builder()
                .success(true)
                .value(value)
                .data(data)
                .build();
    }

    /**
     * Create a failure result.
     */
    public static NormalizationResult fail(String errorMessage) {
        return NormalizationResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
