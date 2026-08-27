package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Represents a complete business scenario configuration.
 * <p>
 * Example task types: ORDER_UPDATE, ORDER_CREATE, ISSUE_HANDLE, ORDER_CANCEL.
 * <p>
 * A config is versioned: once a Task is created, it binds to a specific config version
 * and uses that version throughout its lifecycle. This prevents mid-task behavior changes
 * when an admin publishes a new config version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskConfig {

    /** Task type identifier, e.g. "ORDER_UPDATE" */
    private String taskType;

    /** Config version number */
    private Integer version;

    /** Human-readable name */
    private String name;

    /** Description of this task scenario */
    private String description;

    /** Intent keywords for fast keyword matching (from @AiTask.keywords). */
    private List<String> keywords;

    /** Example user expressions for LLM few-shot intent classification (from @AiTask.examples). */
    private List<String> examples;

    /** Ordered list of field definitions */
    private List<FieldDefinition> fields;

    /** Completion criteria configuration */
    private CompletionConfig completion;

    /** Action to execute when all fields are collected */
    private ActionConfig action;

    /** Extension points for future use */
    private Map<String, Object> extensions;

    /**
     * Find a field definition by code.
     * @return the FieldDefinition, or null if not found
     */
    public FieldDefinition getField(String fieldCode) {
        if (fields == null) return null;
        return fields.stream()
                .filter(f -> f.getCode().equals(fieldCode))
                .findFirst()
                .orElse(null);
    }
}
