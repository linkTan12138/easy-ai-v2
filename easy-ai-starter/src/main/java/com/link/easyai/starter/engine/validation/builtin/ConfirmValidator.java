package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.AiValidator;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CONFIRM validator: validates a user confirmation value against the field's
 * options (e.g. 确认=1 / 其他=0) and attaches a "confirmed" flag to the field data.
 * <p>
 * The value is matched and transformed like ENUM (label -> value), then
 * {@code data.confirmed} is set to true/false by comparing the standard value
 * with the confirm value (params "confirmValue", default 1).
 * <p>
 * Downstream logic (CompletionEngine / Actions) can read the "confirmed" flag
 * from field data to decide whether to proceed or cancel.
 */
@AiValidator("CONFIRM")
public class ConfirmValidator implements FieldValidator {

    public static final String CODE_NO_OPTIONS = "CONFIRM_NO_OPTIONS";
    public static final String CODE_INVALID = "CONFIRM_VALUE_INVALID";

    @Override
    public String type() {
        return "CONFIRM";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        FieldDefinition field = context != null ? context.getFieldDefinition() : null;
        List<OptionDefinition> options = field != null ? field.getOptions() : null;
        if (options == null || options.isEmpty()) {
            return ValidationResult.fail(rawValue, CODE_NO_OPTIONS,
                    "字段未配置确认选项，无法校验");
        }

        String s = ValueUtils.asString(rawValue);
        OptionDefinition matched = null;
        for (OptionDefinition option : options) {
            if (option.getLabel() != null && option.getLabel().trim().equals(s)) {
                matched = option;
                break;
            }
            if (option.getValue() != null && String.valueOf(option.getValue()).trim().equals(s)) {
                matched = option;
                break;
            }
        }

        if (matched == null) {
            List<String> labels = new ArrayList<>();
            for (OptionDefinition option : options) {
                labels.add(option.getLabel() != null ? option.getLabel() : String.valueOf(option.getValue()));
            }
            return ValidationResult.fail(rawValue, CODE_INVALID,
                    String.format("“%s”无效，请回复：%s", s, String.join(" / ", labels)));
        }

        Object confirmValue = params != null && params.get("confirmValue") != null
                ? params.get("confirmValue")
                : 1;
        boolean confirmed = String.valueOf(matched.getValue()).trim()
                .equals(String.valueOf(confirmValue).trim());

        Map<String, Object> data = Map.of("confirmed", confirmed);
        return ValidationResult.success(rawValue, matched.getValue(), data);
    }
}
