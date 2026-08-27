package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares simple field dependencies: this field only participates in
 * collection after all referenced fields exist.
 * <p>
 * Example:
 * <pre>
 * &#64;AiDependsOn("customerNos")
 * private ConfirmType isConfirm;
 * </pre>
 * Multiple dependencies are combined with AND. Complex premise trees
 * (OR / NOT / nested conditions) are intentionally not supported in v1.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiDependsOn {

    /**
     * Field codes this field depends on (all must exist).
     */
    String[] value();
}
