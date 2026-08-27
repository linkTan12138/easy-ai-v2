package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares how the LLM should extract this field from natural language.
 * <p>
 * Maps to {@link com.link.easyai.starter.engine.config.ExtractionConfig}.
 * {@code allowEmpty} is derived from the field's {@code required} flag
 * (required → allowEmpty=false, optional → allowEmpty=true).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiExtract {

    /**
     * Human-readable description of what this field means, sent to the LLM.
     */
    String description() default "";

    /**
     * Concrete examples of valid values, sent to the LLM.
     */
    String[] examples() default {};

    /**
     * Extraction rules / constraints, sent to the LLM.
     */
    String[] rules() default {};
}
