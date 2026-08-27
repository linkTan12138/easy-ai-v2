package com.link.easyai.starter.engine.validation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable AI field validator.
 * <p>
 * The {@link #value()} is the type identifier used in configuration JSON to reference this validator.
 * <p>
 * Example:
 * <pre>
 * @AiValidator("CHANNEL_EXIST")
 * public class ChannelExistValidator implements FieldValidator { ... }
 * </pre>
 * <p>
 * This annotation is meta-annotated with @Component, so the bean is auto-detected by Spring.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiValidator {

    /**
     * Validator type identifier, e.g. "ENUM", "CUSTOMER_EXIST".
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();
}
