package com.link.easyai.starter.engine.premise;

import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Default implementation of PremiseEngine.
 * <p>
 * Supports a simple condition tree with AND/OR operators and basic leaf operators:
 * exists, notExists, eq, neq, in.
 * <p>
 * This replaces the old premiseFields logic in {@link com.link.easyai.starter.service.AiSceneProcessor}
 * which was a simple "check if premise fields exist in stored params" check.
 */
@Component
public class DefaultPremiseEngine implements PremiseEngine, PremiseEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DefaultPremiseEngine.class);

    @Override
    public boolean evaluate(PremiseConfig config, TaskState state) {
        if (config == null) {
            return true;
        }

        // If this config has a "field" and "conditionOperator", it's a leaf condition
        if (config.getField() != null && config.getConditionOperator() != null) {
            return evaluateLeaf(config, state);
        }

        // Otherwise it's a composite condition with sub-conditions
        List<PremiseConfig> conditions = config.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        String operator = config.getOperator() != null ? config.getOperator().toUpperCase() : "AND";

        // NOT 操作符：单操作数取反
        if ("NOT".equals(operator)) {
            if (conditions == null || conditions.size() != 1) {
                log.warn("NOT 操作符需要恰好一个子条件，实际: {}", conditions == null ? 0 : conditions.size());
                return true;
            }
            return !evaluate(conditions.get(0), state);
        }

        for (PremiseConfig subConfig : conditions) {
            boolean result = evaluate(subConfig, state);
            if ("AND".equals(operator) && !result) {
                return false;
            }
            if ("OR".equals(operator) && result) {
                return true;
            }
        }

        return "AND".equals(operator);
    }

    /**
     * Evaluate a leaf condition: field + operator + expected value(s).
     */
    private boolean evaluateLeaf(PremiseConfig config, TaskState state) {
        String field = config.getField();
        String op = config.getConditionOperator().toUpperCase();
        FieldState fieldState = state.getField(field);

        switch (op) {
            case "EXISTS":
                // A field "exists" only when it has a usable value:
                // VALID (validation passed) or CONFIRMED (user confirmed).
                // INVALID (validation failed), EXTRACTED (not yet validated),
                // PENDING (not collected), and SKIPPED (no value) do NOT count.
                return fieldState != null
                        && (fieldState.getStatus() == FieldStatus.VALID
                        || fieldState.getStatus() == FieldStatus.CONFIRMED);

            case "NOT_EXISTS":
                return fieldState == null
                        || (fieldState.getStatus() != FieldStatus.VALID
                        && fieldState.getStatus() != FieldStatus.CONFIRMED);

            case "EQ":
                if (fieldState == null) return false;
                return Objects.equals(String.valueOf(fieldState.getValue()), String.valueOf(config.getValue()));

            case "NEQ":
                if (fieldState == null) return true;
                return !Objects.equals(String.valueOf(fieldState.getValue()), String.valueOf(config.getValue()));

            case "IN":
                if (fieldState == null || config.getValues() == null) return false;
                String fieldValue = String.valueOf(fieldState.getValue());
                return config.getValues().stream()
                        .anyMatch(v -> Objects.equals(fieldValue, String.valueOf(v)));

            default:
                log.warn("Unknown premise operator: {}, treating as true", op);
                return true;
        }
    }
}
