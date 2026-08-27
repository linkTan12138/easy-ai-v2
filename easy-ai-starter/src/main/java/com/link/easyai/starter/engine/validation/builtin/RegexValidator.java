package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.AiValidator;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * REGEX validator: checks string values against a regular expression.
 * <p>
 * Params:
 * <pre>
 * { "pattern": "^[A-Z]{2}\\d{6}$", "message": "格式不正确" }
 * </pre>
 * Supports list values: every element must match.
 */
@AiValidator("REGEX")
public class RegexValidator implements FieldValidator {

    public static final String CODE_MISMATCH = "REGEX_MISMATCH";
    public static final String CODE_NO_PATTERN = "REGEX_NO_PATTERN";

    @Override
    public String type() {
        return "REGEX";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        String pattern = params != null && params.get("pattern") != null
                ? String.valueOf(params.get("pattern"))
                : null;
        if (pattern == null || pattern.isBlank()) {
            return ValidationResult.fail(rawValue, CODE_NO_PATTERN, "REGEX 校验器未配置 pattern 参数");
        }

        Pattern compiled = Pattern.compile(pattern);
        List<Object> elements = ValueUtils.elements(rawValue);

        for (Object element : elements) {
            String s = ValueUtils.asString(element);
            if (s == null || !compiled.matcher(s).matches()) {
                String message = params != null && params.get("message") != null
                        ? String.valueOf(params.get("message"))
                        : String.format("“%s”格式不正确", ValueUtils.asString(element));
                return ValidationResult.fail(rawValue, CODE_MISMATCH, message);
            }
        }

        return ValidationResult.success(rawValue);
    }
}
