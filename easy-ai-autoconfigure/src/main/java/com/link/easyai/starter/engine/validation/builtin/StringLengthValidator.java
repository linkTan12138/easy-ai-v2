package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.AiValidator;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.List;
import java.util.Map;

/**
 * STRING_LENGTH validator: checks string length bounds (inclusive).
 * <p>
 * Params:
 * <pre>
 * { "min": 1, "max": 50 }
 * </pre>
 * Supports list values: every element is checked.
 */
@AiValidator("STRING_LENGTH")
public class StringLengthValidator implements FieldValidator {

    public static final String CODE_OUT_OF_RANGE = "STRING_LENGTH_OUT_OF_RANGE";

    @Override
    public String type() {
        return "STRING_LENGTH";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        Integer min = intParam(params, "min");
        Integer max = intParam(params, "max");
        if (min == null && max == null) {
            return ValidationResult.success(rawValue);
        }

        List<Object> elements = ValueUtils.elements(rawValue);
        for (Object element : elements) {
            String s = ValueUtils.asString(element);
            int len = s == null ? 0 : s.length();
            boolean tooShort = min != null && len < min;
            boolean tooLong = max != null && len > max;
            if (tooShort || tooLong) {
                String message = min != null && max != null
                        ? String.format("“%s”长度必须在 %d-%d 个字符之间", s, min, max)
                        : min != null
                        ? String.format("“%s”长度不能少于 %d 个字符", s, min)
                        : String.format("“%s”长度不能超过 %d 个字符", s, max);
                return ValidationResult.fail(rawValue, CODE_OUT_OF_RANGE, message);
            }
        }
        return ValidationResult.success(rawValue);
    }

    private Integer intParam(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null) return null;
        try {
            return Integer.valueOf(String.valueOf(params.get(key)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
