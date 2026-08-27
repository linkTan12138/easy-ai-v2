package com.link.easyai.starter.engine.validation;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.ValidationConfig;
import com.link.easyai.starter.engine.config.ValidatorDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.extraction.ExtractionResult;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Default implementation of {@link ValidationEngine}.
 * <p>
 * For each field the LLM extracted, this engine runs the field's validator
 * pipeline in configured order:
 * <pre>
 * rawValue -> Validator1 -> Validator2 -> ... -> standard value + merged data
 * </pre>
 * Each validator receives the output of the previous one. The first failing
 * validator determines the field's error code and message.
 * <p>
 * After validation, each field's {@link FieldState} in the {@link TaskState}
 * is updated:
 * - VALID:  rawValue / value / data recorded, errors cleared
 * - INVALID: rawValue / errorCode / errorMessage recorded, value cleared
 * <p>
 * The onFail strategy from {@link ValidationConfig} controls what happens on
 * failure:
 * - RETRY (default): field marked INVALID, the engine re-asks the user
 * - BLOCK: field marked INVALID and the whole task is marked FAILED
 *   (no exception is thrown, so the task state is still persisted by the caller)
 */
@Component
public class DefaultValidationEngine implements ValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultValidationEngine.class);

    private static final String ON_FAIL_BLOCK = "BLOCK";

    private final ValidatorRegistry registry;

    @Autowired
    public DefaultValidationEngine(ValidatorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void validate(ExtractionResult extraction,
                         AiTaskConfig config,
                         TaskState state,
                         TaskContext context) {
        if (extraction == null || !extraction.isSuccess()) {
            log.debug("[ValidationEngine] extraction failed or null, nothing to validate");
            return;
        }
        Map<String, Object> extracted = extraction.getFields();
        if (extracted == null || extracted.isEmpty()) {
            log.debug("[ValidationEngine] no fields extracted, nothing to validate");
            return;
        }

        for (Map.Entry<String, Object> entry : extracted.entrySet()) {
            String fieldCode = entry.getKey();
            Object rawValue = entry.getValue();

            // LLM returned an unknown field code -> ignore (never trust LLM output blindly)
            FieldDefinition field = config.getField(fieldCode);
            if (field == null) {
                log.warn("[ValidationEngine] LLM returned unknown field '{}', ignored", fieldCode);
                continue;
            }

            // Empty value means the LLM did not extract this field in this turn
            if (isEmptyValue(rawValue)) {
                log.debug("[ValidationEngine] field '{}' has empty value, skipped", fieldCode);
                continue;
            }

            FieldContext fieldContext = buildFieldContext(field, state, context);
            ValidationResult result = validateField(field, rawValue, fieldContext);
            applyResult(field, rawValue, result, state);

            if (!result.isValid() && isBlock(field)) {
                log.warn("[ValidationEngine] field '{}' failed with onFail=BLOCK, task {} marked FAILED",
                        fieldCode, state.getTaskId());
                state.setStatus(TaskStatus.FAILED);
            }
        }
    }

    /**
     * Run the validator pipeline for a single field.
     */
    private ValidationResult validateField(FieldDefinition field,
                                           Object rawValue,
                                           FieldContext context) {
        ValidationConfig validation = field.getValidation();
        if (validation == null
                || validation.getValidators() == null
                || validation.getValidators().isEmpty()) {
            // No validation configured -> accept the raw value as-is
            return ValidationResult.success(rawValue);
        }

        Object currentValue = rawValue;
        Map<String, Object> data = new LinkedHashMap<>();

        for (ValidatorDefinition definition : validation.getValidators()) {
            FieldValidator validator = registry.get(definition.getType());
            if (validator == null) {
                log.error("[ValidationEngine] validator '{}' not registered (field '{}')",
                        definition.getType(), field.getCode());
                return ValidationResult.fail(rawValue, "VALIDATOR_NOT_FOUND",
                        "校验器未注册: " + definition.getType());
            }

            ValidationResult result;
            try {
                result = validator.validate(currentValue, context, definition.getParams());
            } catch (Exception e) {
                log.error("[ValidationEngine] validator '{}' threw exception (field '{}')",
                        definition.getType(), field.getCode(), e);
                return ValidationResult.fail(rawValue, "VALIDATOR_ERROR",
                        "校验器执行异常: " + e.getMessage());
            }

            if (result == null) {
                log.error("[ValidationEngine] validator '{}' returned null (field '{}')",
                        definition.getType(), field.getCode());
                return ValidationResult.fail(rawValue, "VALIDATOR_ERROR",
                        "校验器返回空结果: " + definition.getType());
            }

            if (!result.isValid()) {
                if (result.getRawValue() == null) {
                    result.setRawValue(rawValue);
                }
                log.info("[ValidationEngine] field '{}' failed validator '{}': {}",
                        field.getCode(), definition.getType(), result.getMessage());
                return result;
            }

            // Pipeline chaining: next validator receives the transformed value
            if (result.getValue() != null) {
                currentValue = result.getValue();
            }
            if (result.getData() != null && !result.getData().isEmpty()) {
                data.putAll(result.getData());
            }
        }

        return ValidationResult.success(rawValue, currentValue, data);
    }

    /**
     * Apply the pipeline result to the field state.
     */
    private void applyResult(FieldDefinition field,
                             Object rawValue,
                             ValidationResult result,
                             TaskState state) {
        FieldState existing = state.getField(field.getCode());
        FieldState.FieldStateBuilder builder = existing != null
                ? existing.toBuilder()
                : FieldState.builder().field(field.getCode()).version(0);

        if (result.isValid()) {
            builder.status(FieldStatus.VALID)
                    .rawValue(result.getRawValue() != null ? result.getRawValue() : rawValue)
                    .value(result.getValue())
                    .displayValue(result.getDisplayValue())
                    .data(result.getData())
                    .errorCode(null)
                    .errorMessage(null);
        } else {
            builder.status(FieldStatus.INVALID)
                    .rawValue(rawValue)
                    .value(null)
                    .data(null)
                    .errorCode(result.getErrorCode())
                    .errorMessage(result.getMessage());
        }

        FieldState fieldState = builder.build();
        fieldState.setVersion(fieldState.getVersion() == null ? 1 : fieldState.getVersion() + 1);
        state.putField(field.getCode(), fieldState);

        log.debug("[ValidationEngine] field '{}' -> {} (value={})",
                field.getCode(), fieldState.getStatus(), fieldState.getValue());
    }

    /**
     * Build the per-field context from the task state and task context.
     */
    private FieldContext buildFieldContext(FieldDefinition field,
                                           TaskState state,
                                           TaskContext context) {
        Map<String, Object> taskContextMap = new HashMap<>();
        if (context != null) {
            if (context.getTenantId() != null) {
                taskContextMap.put("tenantId", context.getTenantId());
            }
            if (context.getUserDetails() != null) {
                taskContextMap.put("userDetails", context.getUserDetails());
            }
            if (context.getData() != null) {
                taskContextMap.putAll(context.getData());
            }
        }

        return FieldContext.builder()
                .taskId(state.getTaskId())
                .taskType(state.getTaskType())
                .configVersion(state.getConfigVersion())
                .fieldCode(field.getCode())
                .fieldDefinition(field)
                .taskContext(taskContextMap)
                .fieldStates(state.getFields() != null
                        ? new LinkedHashMap<>(state.getFields())
                        : new LinkedHashMap<>())
                .build();
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private boolean isBlock(FieldDefinition field) {
        return field.getValidation() != null
                && ON_FAIL_BLOCK.equalsIgnoreCase(field.getValidation().getOnFail());
    }
}
