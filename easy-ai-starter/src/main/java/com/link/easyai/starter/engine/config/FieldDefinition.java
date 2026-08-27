package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * The core field definition object of the AI Task Engine.
 * <p>
 * Describes everything the engine needs to know about a single field:
 * - How to extract it (prompt description, examples, rules)
 * - When to collect it (premise / pre-conditions)
 * - How to validate it (validator pipeline)
 * - How to normalize it (normalizer)
 * - How to map it to action parameters
 * <p>
 * This object is loaded from configuration (JSON or DB), never from hardcoded Java.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDefinition {

    /** Unique field code within a task, e.g. "customerNos", "declareType" */
    private String code;

    /** Human-readable field name, e.g. "客户单号" */
    private String name;

    /** Framework field type */
    private FieldType type;

    /** Whether this field is required for task completion */
    @Builder.Default
    private boolean required = false;

    /** Extraction configuration (prompt description, examples, rules) */
    private ExtractionConfig extraction;

    /** Premise: pre-condition for this field to participate in collection */
    private PremiseConfig premise;

    /** Validation pipeline configuration */
    private ValidationConfig validation;

    /** Normalization configuration (optional, for complex standardization) */
    private NormalizationConfig normalization;

    /** Mapping rules: how to map validated value to action parameters */
    private List<MappingRule> mappings;

    /** Enum options for this field (if applicable) */
    private List<OptionDefinition> options;

    /** Collection order; lower numbers are collected first */
    private Integer order;

    /** Whether this field contains sensitive data (masked in logs) */
    @Builder.Default
    private boolean sensitive = false;
}
