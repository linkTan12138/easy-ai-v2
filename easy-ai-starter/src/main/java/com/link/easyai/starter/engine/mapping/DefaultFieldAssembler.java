package com.link.easyai.starter.engine.mapping;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.MappingRule;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link FieldAssembler}.
 * <p>
 * Evaluates the field's {@link MappingRule}s against the validation result.
 * Supported source expressions:
 * <ul>
 *   <li>{@code $value}    — the validated/normalized value</li>
 *   <li>{@code $rawValue} — the raw LLM-extracted value</li>
 *   <li>{@code $data.xxx} — a value from the validation data map</li>
 * </ul>
 * A rule with an unresolvable source (e.g. missing data key) is skipped with a
 * warning instead of failing the whole mapping.
 */
@Component
public class DefaultFieldAssembler implements FieldAssembler {

    private static final Logger log = LoggerFactory.getLogger(DefaultFieldAssembler.class);

    private static final String SOURCE_VALUE = "$value";
    private static final String SOURCE_RAW_VALUE = "$rawValue";
    private static final String SOURCE_DATA_PREFIX = "$data.";

    @Override
    public List<FieldValue> assemble(FieldDefinition definition,
                                     ValidationResult result,
                                     FieldContext context) {
        List<FieldValue> values = new ArrayList<>();
        if (definition == null || definition.getMappings() == null || result == null) {
            return values;
        }

        for (MappingRule rule : definition.getMappings()) {
            if (rule == null || rule.getTarget() == null || rule.getTarget().isBlank()) {
                continue;
            }
            Object value = resolveSource(rule.getSource(), result);
            if (value == null) {
                log.debug("[FieldAssembler] rule source '{}' unresolvable for field '{}', skipped",
                        rule.getSource(), definition.getCode());
                continue;
            }
            values.add(FieldValue.builder()
                    .target(rule.getTarget().trim())
                    .value(value)
                    .build());
        }
        return values;
    }

    /**
     * Resolve a source expression against the validation result.
     *
     * @return the resolved value, or null if unresolvable
     */
    private Object resolveSource(String source, ValidationResult result) {
        if (source == null || source.isBlank()) {
            // No explicit source: default to $value
            return result.getValue();
        }
        String expr = source.trim();
        if (SOURCE_VALUE.equals(expr)) {
            return result.getValue();
        }
        if (SOURCE_RAW_VALUE.equals(expr)) {
            return result.getRawValue();
        }
        if (expr.startsWith(SOURCE_DATA_PREFIX)) {
            String key = expr.substring(SOURCE_DATA_PREFIX.length()).trim();
            if (result.getData() != null) {
                return result.getData().get(key);
            }
            return null;
        }
        // Unknown expression: treat a bare string as a literal constant
        if (expr.startsWith("$")) {
            log.warn("[FieldAssembler] unknown source expression '{}', skipped", expr);
            return null;
        }
        return expr;
    }
}
