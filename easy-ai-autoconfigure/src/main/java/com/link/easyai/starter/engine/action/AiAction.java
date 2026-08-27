package com.link.easyai.starter.engine.action;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable AI task action executor.
 * <p>
 * The {@link #value()} is the type identifier used in configuration JSON.
 * The optional {@link #name()}, {@link #description()}, {@link #triggers()}
 * provide metadata used by feature-intro / help actions to dynamically list
 * all available capabilities — no hard-coded feature catalog needed.
 * <p>
 * Example:
 * <pre>
 * &#64;AiAction(value = "UPDATE_WAYBILL",
 *           name = "修改运单",
 *           description = "按运单号查询并修改收货渠道、目的国等字段。",
 *           triggers = {"修改运单", "改一下运单"})
 * public class UpdateWaybillAction implements ActionExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiAction {

    /**
     * Action type identifier, e.g. "UPDATE_WAYBILL".
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();

    /**
     * Human-readable feature name, e.g. "修改运单".
     * Used by feature-intro / help actions. Empty string falls back to type.
     */
    String name() default "";

    /**
     * Short description of what this action does.
     * Used by feature-intro / help actions.
     */
    String description() default "";

    /**
     * Example trigger phrases users might say to invoke this action.
     * Used by feature-intro / help actions and potentially by intent engines.
     */
    String[] triggers() default {};

    /**
     * If true, this action is excluded from dynamic feature listings
     * (e.g. built-in demo actions, meta-actions like feature-intro itself).
     */
    boolean hidden() default false;
}
