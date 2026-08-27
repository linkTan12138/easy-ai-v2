package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Defines how the LLM should extract a field from natural language.
 * Replaces the old "judgmentLogic" free-text with structured rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionConfig {

    /** Human-readable description of what this field means, sent to LLM */
    private String description;

    /** Concrete examples of valid values, sent to LLM */
    private List<String> examples;

    /** Extraction rules / constraints, sent to LLM */
    private List<String> rules;

    /** If true, LLM may return empty/null for this field */
    @Builder.Default
    private boolean allowEmpty = false;

    /** Extra params for custom extraction strategies */
    private Map<String, Object> params;
}
