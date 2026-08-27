package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the default field metadata derived from the Java field.
 * <p>
 * Everything not declared here is derived by convention:
 * <ul>
 *   <li>{@code code}  = Java field name</li>
 *   <li>{@code type}  = Java field type (String → STRING, List&lt;String&gt; → STRING_LIST,
 *       enum → value type + auto-generated options)</li>
 *   <li>{@code order} = field declaration order (1-based)</li>
 *   <li>{@code name}  = field name (fallback when not declared)</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiField {

    /**
     * Human-readable field name, e.g. "客户单号".
     * Defaults to the Java field name.
     */
    String name() default "";

    /**
     * Whether this field is required for task completion.
     */
    boolean required() default false;

    /**
     * Whether this field contains sensitive data (masked in logs).
     */
    boolean sensitive() default false;

    /**
     * Normalizer type for complex standardization, e.g. "CARGO_DESC".
     * Empty (default) means no normalization. Maps 1:1 to
     * {@link com.link.easyai.starter.engine.config.NormalizationConfig#getType()}.
     */
    String normalize() default "";
}
