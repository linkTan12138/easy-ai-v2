package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.exception.ConfigValidationException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads enum constants into {@link OptionDefinition} lists.
 * <p>
 * Convention (checked at startup, no annotation needed):
 * <ul>
 *   <li>{@code getValue()} — the internal value (Integer/Long/String/Boolean);
 *       falls back to the constant name when absent</li>
 *   <li>{@code getLabel()} — the display label; falls back to the constant name</li>
 * </ul>
 * Example:
 * <pre>
 * public enum DeclareType {
 *     OTHER(0, "其他"),
 *     SEPARATE(1, "单独报关"),
 *     BUY_ORDER(3, "买单报关");
 *
 *     private final Integer value;
 *     private final String label;
 *     // constructor + getters
 * }
 * </pre>
 */
public final class EnumResolver {

    private EnumResolver() {
    }

    /**
     * Resolve the framework field type from the enum's value type.
     * Integer-valued enums map to INTEGER (the common case for legacy DB
     * configs), String-valued enums to STRING.
     */
    public static FieldType fieldType(Class<? extends Enum<?>> enumType) {
        Enum<?>[] constants = requireConstants(enumType);
        return fieldTypeOf(value(constants[0], enumType), enumType);
    }

    /**
     * Build the option list (label + value) from the enum constants,
     * in declaration order.
     */
    public static List<OptionDefinition> options(Class<? extends Enum<?>> enumType) {
        Enum<?>[] constants = requireConstants(enumType);
        List<OptionDefinition> options = new ArrayList<>(constants.length);
        for (Enum<?> constant : constants) {
            options.add(OptionDefinition.builder()
                    .label(label(constant))
                    .value(value(constant, enumType))
                    .build());
        }
        return options;
    }

    private static Enum<?>[] requireConstants(Class<? extends Enum<?>> enumType) {
        Enum<?>[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new ConfigValidationException(
                    "枚举 " + enumType.getName() + " 没有常量，无法生成选项");
        }
        return constants;
    }

    private static Object value(Enum<?> constant, Class<? extends Enum<?>> enumType) {
        Method getter = findGetter(constant, "getValue");
        if (getter != null) {
            try {
                return getter.invoke(constant);
            } catch (Exception e) {
                throw new ConfigValidationException(
                        "读取枚举值失败: " + enumType.getName() + "." + constant.name(), e);
            }
        }
        return constant.name();
    }

    private static String label(Enum<?> constant) {
        Method getter = findGetter(constant, "getLabel");
        if (getter != null) {
            try {
                Object label = getter.invoke(constant);
                if (label != null) {
                    return String.valueOf(label);
                }
            } catch (Exception ignored) {
                // fall through to the constant name
            }
        }
        return constant.name();
    }

    private static Method findGetter(Enum<?> constant, String name) {
        try {
            Method method = constant.getClass().getMethod(name);
            if (method.getParameterCount() == 0) {
                return method;
            }
        } catch (NoSuchMethodException ignored) {
            // convention not implemented — fallback applies
        }
        return null;
    }

    private static FieldType fieldTypeOf(Object value, Class<? extends Enum<?>> enumType) {
        if (value instanceof Integer || value instanceof Long) {
            return FieldType.INTEGER;
        }
        if (value instanceof String) {
            return FieldType.STRING;
        }
        if (value instanceof Boolean) {
            return FieldType.BOOLEAN;
        }
        throw new ConfigValidationException(String.format(
                "枚举 %s 的 value 类型不被支持: %s（支持 Integer/Long/String/Boolean）",
                enumType.getName(),
                value == null ? "null" : value.getClass().getSimpleName()));
    }
}
