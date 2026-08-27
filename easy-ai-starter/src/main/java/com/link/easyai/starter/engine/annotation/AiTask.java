package com.link.easyai.starter.engine.annotation;

import com.link.easyai.starter.engine.action.ActionExecutor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an AI Task on a DTO class, replacing the DB JSON configuration.
 * <p>
 * The annotated class is scanned at startup, its fields are converted into
 * {@link com.link.easyai.starter.engine.config.AiTaskConfig}, and the existing
 * {@link com.link.easyai.starter.engine.AiTaskEngine} executes the task unchanged.
 * <p>
 * Usage:
 * <pre>
 * &#64;AiTask(
 *     type = "ORDER_UPDATE",
 *     name = "修改订单",
 *     description = "通过多轮对话收集修改订单所需字段",
 *     action = UpdateOrderAction.class,
 *     postActions = {"LOG"}
 * )
 * public class UpdateOrderDto { ... }
 * </pre>
 * <p>
 * Field {@code code} / {@code type} / {@code order} are derived from the Java
 * field itself — they are never declared here.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiTask {

    /**
     * Task type identifier, e.g. "ORDER_UPDATE".
     * Must be non-blank and unique across all @AiTask classes.
     */
    String type();

    /**
     * Human-readable task name, e.g. "修改订单".
     */
    String name();

    /**
     * Description of the task scenario.
     */
    String description() default "";

    /**
     * The main action executed when all fields are collected.
     * Resolved as a Spring bean at startup; the action's {@code type()}
     * becomes the {@link com.link.easyai.starter.engine.config.ActionConfig} type.
     */
    Class<? extends ActionExecutor> action();

    /**
     * Post-actions executed after the main action succeeds,
     * e.g. {"LOG", "WRITE_TRACK"}.
     */
    String[] postActions() default {};

    /**
     * Intent keywords for fast keyword matching and LLM few-shot examples.
     * Used by the intent recognition engine to match user messages to this task.
     * Example: {"修改订单", "改订单", "更新订单", "modify order"}
     */
    String[] keywords() default {};

    /**
     * Example user expressions for this task, used in LLM intent classification
     * few-shot prompts to improve recognition accuracy.
     * Example: {"帮我把订单的收货渠道改一下", "订单 US123 的国家要改成美国"}
     */
    String[] examples() default {};
}
