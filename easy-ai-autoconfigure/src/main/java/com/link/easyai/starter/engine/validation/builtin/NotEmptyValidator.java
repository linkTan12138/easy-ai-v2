package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.AiValidator;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.Map;

/**
 * NOT_EMPTY validator: rejects null, blank strings, and empty collections.
 */
@AiValidator("NOT_EMPTY")
public class NotEmptyValidator implements FieldValidator {

    public static final String CODE_EMPTY = "VALUE_EMPTY";

    @Override
    public String type() {
        return "NOT_EMPTY";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        if (ValueUtils.isBlank(rawValue)) {
            String fieldName = context != null && context.getFieldDefinition() != null
                    ? context.getFieldDefinition().getName()
                    : context != null ? context.getFieldCode() : "字段";
            return ValidationResult.fail(rawValue, CODE_EMPTY, fieldName + "不能为空");
        }
        return ValidationResult.success(rawValue);
    }
}
