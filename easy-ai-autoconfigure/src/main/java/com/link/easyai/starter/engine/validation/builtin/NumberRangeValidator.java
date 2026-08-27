package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.AiValidator;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NUMBER_RANGE validator: checks numeric bounds (inclusive) and coerces
 * numeric strings into numbers.
 * <p>
 * Params:
 * <pre>
 * { "min": 0, "max": 100 }
 * </pre>
 * The transformed value is a narrowed number: whole numbers become
 * Integer/Long, fractional numbers become Double. Supports list values.
 */
@AiValidator("NUMBER_RANGE")
public class NumberRangeValidator implements FieldValidator {

    public static final String CODE_NOT_A_NUMBER = "NOT_A_NUMBER";
    public static final String CODE_OUT_OF_RANGE = "NUMBER_OUT_OF_RANGE";

    @Override
    public String type() {
        return "NUMBER_RANGE";
    }

    @Override
    public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
        BigDecimal min = decimalParam(params, "min");
        BigDecimal max = decimalParam(params, "max");

        List<Object> elements = ValueUtils.elements(rawValue);
        List<Object> transformed = new ArrayList<>(elements.size());

        for (Object element : elements) {
            BigDecimal number = toBigDecimal(element);
            if (number == null) {
                return ValidationResult.fail(rawValue, CODE_NOT_A_NUMBER,
                        String.format("“%s”不是有效数字", ValueUtils.asString(element)));
            }
            if ((min != null && number.compareTo(min) < 0)
                    || (max != null && number.compareTo(max) > 0)) {
                String range = min != null && max != null
                        ? String.format("[%s, %s]", min.toPlainString(), max.toPlainString())
                        : min != null ? "≥ " + min.toPlainString() : "≤ " + max.toPlainString();
                return ValidationResult.fail(rawValue, CODE_OUT_OF_RANGE,
                        String.format("“%s”超出范围 %s", number.toPlainString(), range));
            }
            transformed.add(narrow(number));
        }

        Object value = ValueUtils.isList(rawValue) ? transformed : transformed.get(0);
        return ValidationResult.success(rawValue, value, null);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            String s = String.valueOf(value).trim();
            if (s.isEmpty()) return null;
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal decimalParam(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null) return null;
        try {
            return new BigDecimal(String.valueOf(params.get(key)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Narrow a BigDecimal to the smallest practical number type.
     */
    private Number narrow(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            long l = stripped.longValueExact();
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return (int) l;
            }
            return l;
        }
        return stripped.doubleValue();
    }
}
