package com.link.easyai.starter.engine.extraction;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Result of LLM extraction: the raw fields the LLM identified from user input.
 * <p>
 * The LLM is responsible for "what the user said" — not for business validity.
 * Validation happens downstream in {@link com.link.easyai.starter.engine.validation.ValidationEngine}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResult {

    /** Extracted field values, keyed by field code */
    private Map<String, Object> fields;

    /** LLM's reasoning for the extraction (for traceability) */
    private String reason;

    /** Raw LLM response (for debugging) */
    private String rawResponse;

    /** Whether the extraction call succeeded */
    @Builder.Default
    private boolean success = true;

    /** Error message if extraction failed */
    private String errorMessage;

    /**
     * Create a failed extraction result.
     */
    public static ExtractionResult fail(String errorMessage, String rawResponse) {
        return ExtractionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .rawResponse(rawResponse)
                .build();
    }
}
