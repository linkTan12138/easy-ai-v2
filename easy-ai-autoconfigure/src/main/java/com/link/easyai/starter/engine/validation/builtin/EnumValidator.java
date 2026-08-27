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
import java.util.stream.Collectors;

/**
 * ENUM validator: checks the value against the field's configured options
 * (label or value match) and transforms labels into their standard values.
 * <p>
 * Example: options [{label:"买单报关", value:3}] — raw "买单报关" -> value 3, displayValue "买单报关".
 * <p>
 * Supports list values: every element must match; the transformed value is a list.
 */
@AiValidator("ENUM")
public class EnumValidator implements FieldValidator {

    public static final String CODE_NO_OPTIONS = "ENUM_NO_OPTIONS";
    public static final String CODE_NOT_IN_ENUM = "ENUM_VALUE_INVALID";

    @Override
    public String type() {
        return "ENUM";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        List<OptionDefinition> options = resolveOptions(context, params);
        if (options == null || options.isEmpty()) {
            return ValidationResult.fail(rawValue, CODE_NO_OPTIONS,
                    "字段未配置枚举选项，无法校验");
        }

        List<Object> rawElements = ValueUtils.elements(rawValue);
        List<Object> transformed = new ArrayList<>(rawElements.size());
        List<String> labels = new ArrayList<>(rawElements.size());
        Object firstInvalid = null;

        for (Object element : rawElements) {
            OptionDefinition matched = match(element, options);
            if (matched == null) {
                firstInvalid = element;
                break;
            }
            transformed.add(matched.getValue());
            labels.add(matched.getLabel() != null ? matched.getLabel() : String.valueOf(matched.getValue()));
        }

        if (firstInvalid != null) {
            String allowed = options.stream()
                    .map(o -> String.valueOf(o.getLabel()))
                    .collect(Collectors.joining("/"));
            return ValidationResult.fail(rawValue, CODE_NOT_IN_ENUM,
                    String.format("\u201c%s\u201d不是有效值，可选值：%s", ValueUtils.asString(firstInvalid), allowed));
        }

        boolean isList = ValueUtils.isList(rawValue);
        Object value = isList ? transformed : transformed.get(0);
        String displayValue = isList ? String.join("\u3001", labels) : labels.get(0);
        return ValidationResult.success(rawValue, value, displayValue, null);
    }

    private OptionDefinition match(Object element, List<OptionDefinition> options) {
        if (element == null) return null;
        String s = String.valueOf(element).trim();
        for (OptionDefinition option : options) {
            if (option.getLabel() != null && option.getLabel().trim().equals(s)) {
                return option;
            }
            if (option.getValue() != null && String.valueOf(option.getValue()).trim().equals(s)) {
                return option;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<OptionDefinition> resolveOptions(FieldContext context, Map<String, Object> params) {
        // 1. Options from validator params override field definition options
        if (params != null && params.get("options") instanceof List<?> list) {
            List<OptionDefinition> fromParams = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof OptionDefinition od) {
                    fromParams.add(od);
                } else if (item instanceof Map<?, ?> map) {
                    Object label = map.get("label");
                    Object value = map.get("value");
                    if (label != null || value != null) {
                        fromParams.add(OptionDefinition.builder()
                                .label(label == null ? null : String.valueOf(label))
                                .value(value)
                                .build());
                    }
                }
            }
            if (!fromParams.isEmpty()) {
                return fromParams;
            }
        }

        // 2. Options from field definition
        FieldDefinition field = context != null ? context.getFieldDefinition() : null;
        return field != null ? field.getOptions() : null;
    }
}
