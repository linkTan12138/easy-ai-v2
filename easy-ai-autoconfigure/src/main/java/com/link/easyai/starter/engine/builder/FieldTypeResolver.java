package com.link.easyai.starter.engine.builder;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;

/**
 * Resolves the framework {@link com.link.easyai.starter.engine.config.FieldType}
 * (and enum options, if any) from a Java field.
 * <p>
 * Supported mappings:
 * <pre>
 * String / CharSequence        → STRING
 * Integer / int                → INTEGER
 * Long / long                  → LONG
 * Double / Float / BigDecimal  → DECIMAL
 * Boolean / boolean            → BOOLEAN
 * List&lt;String&gt;               → STRING_LIST
 * List&lt;Integer&gt;              → INTEGER_LIST
 * enum (Integer values)        → INTEGER  + options
 * enum (String values)         → STRING   + options
 * </pre>
 * Java types that cannot be mapped (arrays, Maps, raw List, List&lt;enum&gt;, ...)
 * fail config building with a clear error — silent fallbacks hide typos.
 */
public final class FieldTypeResolver {

    private FieldTypeResolver() {
    }

    /**
     * Result of resolving a Java field: the framework field type plus the
     * enum options when the field is a Java enum.
     */
    public record Resolution(
            com.link.easyai.starter.engine.config.FieldType type,
            List<com.link.easyai.starter.engine.config.OptionDefinition> options,
            Class<? extends Enum<?>> enumType) {

        public boolean isEnum() {
            return enumType != null;
        }
    }

    /**
     * Resolve a Java field into the framework field type.
     *
     * @throws com.link.easyai.starter.engine.exception.ConfigValidationException
     *         if the Java type is not supported
     */
    public static Resolution resolve(Field field) {
        Class<?> raw = field.getType();
        Type generic = field.getGenericType();

        if (String.class.equals(raw)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.STRING, null, null);
        }
        if (Integer.class.equals(raw) || int.class.equals(raw)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.INTEGER, null, null);
        }
        if (Long.class.equals(raw) || long.class.equals(raw)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.LONG, null, null);
        }
        if (Double.class.equals(raw) || double.class.equals(raw)
                || Float.class.equals(raw) || float.class.equals(raw)
                || BigDecimal.class.equals(raw)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.DECIMAL, null, null);
        }
        if (Boolean.class.equals(raw) || boolean.class.equals(raw)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.BOOLEAN, null, null);
        }
        if (List.class.equals(raw)) {
            return resolveList(field, generic);
        }
        if (raw.isEnum()) {
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) raw;
            return new Resolution(EnumResolver.fieldType(enumType), EnumResolver.options(enumType), enumType);
        }

        throw unsupported(field, raw.getSimpleName());
    }

    private static Resolution resolveList(Field field, Type generic) {
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw unsupported(field, "raw List (请声明元素类型，如 List<String>)");
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (!(argument instanceof Class<?> elementClass)) {
            throw unsupported(field, "List with non-class element type");
        }
        if (elementClass.isEnum()) {
            throw unsupported(field, "List<" + elementClass.getSimpleName()
                    + "> (v1 不支持枚举列表，请改用 List<String> 并显式声明 @AiValid)");
        }
        if (String.class.equals(elementClass)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.STRING_LIST, null, null);
        }
        if (Integer.class.equals(elementClass) || int.class.equals(elementClass)) {
            return new Resolution(com.link.easyai.starter.engine.config.FieldType.INTEGER_LIST, null, null);
        }
        throw unsupported(field, "List<" + elementClass.getSimpleName() + ">");
    }

    private static com.link.easyai.starter.engine.exception.ConfigValidationException unsupported(
            Field field, String typeName) {
        return new com.link.easyai.starter.engine.exception.ConfigValidationException(String.format(
                "字段 '%s' 的 Java 类型不被支持: %s。支持的类型: String, Integer, Long, Double/BigDecimal, "
                        + "Boolean, List<String>, List<Integer>, Java 枚举",
                field.getName(), typeName));
    }
}
