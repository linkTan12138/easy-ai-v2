package com.link.easyai.starter.engine.task;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标记一个类为 AI 任务执行器（核心注解）。
 * <p>
 * 每个业务场景必须有且仅有一个 {@code @AiTask} 类，定义任务的元信息：
 * <ul>
 *   <li>{@link #value()} - 任务类型唯一标识</li>
 *   <li>{@link #name()} - 功能名称（用户可见，功能介绍用）</li>
 *   <li>{@link #description()} - 功能描述（用户可见）</li>
 *   <li>{@link #triggers()} - 触发关键词（意图识别用）</li>
 *   <li>{@link #postActions()} - 后置动作列表</li>
 *   <li>{@link #hidden()} - 是否隐藏（不出现在功能介绍）</li>
 * </ul>
 * <p>
 * 如果需要参数收集，额外创建一个 {@code @AiTaskParam} 标注的 DTO 类，
 * 通过 {@code type} 与本任务一对一关联。无参数的纯动作场景只需本注解。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiTask(value = "CREATE_TICKET",
 *         name = "创建工单",
 *         description = "根据收集的字段创建客服工单",
 *         triggers = {"创建工单", "我要投诉"},
 *         postActions = {"LOG"})
 * public class CreateTicketTask implements TaskExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiTask {

    /**
     * 任务类型唯一标识，如 "CREATE_TICKET"。
     * 同时作为 Spring Bean 的名称。
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();

    /**
     * 功能名称（用户可见），如 "创建工单"。
     * 为空时回退到 value。
     */
    String name() default "";

    /**
     * 功能描述（用户可见）。
     */
    String description() default "";

    /**
     * 触发关键词，用户可能说的短语，用于意图识别。
     */
    String[] triggers() default {};

    /**
     * 后置动作名称列表，任务执行成功后依次执行，如 {"LOG", "NOTIFY"}。
     */
    String[] postActions() default {};

    /**
     * 是否隐藏，true 时不出现在动态功能列表中（如内置的功能介绍本身）。
     */
    boolean hidden() default false;
}
