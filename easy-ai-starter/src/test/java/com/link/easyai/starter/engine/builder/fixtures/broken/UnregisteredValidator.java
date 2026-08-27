package com.link.easyai.starter.engine.builder.fixtures.broken;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.Map;

/**
 * A validator class that is deliberately NOT registered as a bean in tests —
 * used to verify the "validator bean missing" startup error.
 */
public class UnregisteredValidator implements FieldValidator {

    @Override
    public String type() {
        return "UNREGISTERED";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException("never invoked");
    }
}
