package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares explicit mapping rules for a field.
 * <p>
 * By default a field maps to the same-named target ({@code $value}), so this
 * annotation is only needed when the target differs or extra values are mapped:
 * <pre>
 * &#64;AiMapping({
 *     &#64;Mapping(target = "receiveChannelId", source = "$data.id"),
 *     &#64;Mapping(target = "receiveChannelName", source = "$value")
 * })
 * private String receiveChannelName;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiMapping {

    /**
     * The mapping rules; evaluated in declaration order.
     */
    Mapping[] value();
}
