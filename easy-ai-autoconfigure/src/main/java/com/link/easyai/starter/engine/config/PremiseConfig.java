package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Premise (pre-condition) for a field to participate in collection.
 * <p>
 * First version supports a simple condition tree with AND/OR operators
 * and basic operators: exists, notExists, eq, neq, in.
 * <p>
 * Example JSON:
 * <pre>
 * {
 *   "operator": "AND",
 *   "conditions": [
 *     { "field": "customerNos", "operator": "exists" }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiseConfig {

    /** Logical operator for combining conditions: "AND" or "OR" */
    private String operator;

    /** Nested conditions (leaf-level field checks or nested premise groups) */
    private List<PremiseConfig> conditions;

    /** Field code this condition checks (for leaf conditions) */
    private String field;

    /** Operator for leaf condition: exists, notExists, eq, neq, in */
    private String conditionOperator;

    /** Expected value for eq/neq operators */
    private Object value;

    /** Expected values for "in" operator */
    private List<Object> values;
}
