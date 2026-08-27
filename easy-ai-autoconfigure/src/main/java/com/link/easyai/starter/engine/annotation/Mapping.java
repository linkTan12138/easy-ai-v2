package com.link.easyai.starter.engine.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A single mapping rule: maps a source expression to a target parameter path.
 * <p>
 * Supported source expressions (v1):
 * <ul>
 *   <li>{@code $value}    — the validated/normalized field value</li>
 *   <li>{@code $rawValue} — the raw LLM-extracted value</li>
 *   <li>{@code $data.xxx} — a value from the validation data map</li>
 *   <li>literal string    — a constant value</li>
 * </ul>
 * Only usable inside {@link AiMapping}.
 */
@Target({}) // nested-only: only usable as a value inside @AiMapping
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Mapping {

    /**
     * Target path in the action parameter map, e.g. "receiveChannelId".
     */
    String target();

    /**
     * Source expression: "$value", "$rawValue", "$data.xxx" or a literal.
     */
    String source();
}
