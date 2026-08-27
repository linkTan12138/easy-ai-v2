package com.link.easyai.starter.engine.validation;

import com.link.easyai.starter.engine.context.FieldContext;

import java.util.Map;

/**
 * A single validator in the validation pipeline.
 * <p>
 * Validators are registered by type and looked up by the ValidationEngine.
 * A validator receives the current value (which may be the raw LLM value or
 * the output of a previous validator in the pipeline), validates it, and
 * optionally transforms it.
 * <p>
 * Implementations should be annotated with @AiValidator and registered as Spring beans.
 */
public interface FieldValidator {

    /**
     * Get the type identifier for this validator.
     * E.g. "ENUM", "CUSTOMER_EXIST", "CHANNEL_EXIST"
     */
    String type();

    /**
     * Validate (and optionally transform) the value.
     *
     * @param rawValue the input value (may be transformed by previous validators in pipeline)
     * @param context  the field context (task state, field code, shared data)
     * @param params   validator parameters from configuration
     * @return validation result (valid/invalid, transformed value, attached data)
     */
    ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params);
}
