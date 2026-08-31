package com.link.easyai.starter.engine.task;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标记一个类为 AI 任务后置执行器。
 * <p>
 * 后置任务在主任务执行成功后依次执行，用于日志、通知、审计等。
 * 采用 best-effort 模式，后置任务失败不影响主任务结果。
 * <p>
 * 后置任务类型标识由 {@link #value()} 提供（唯一权威来源），
 * 主任务通过 {@code @AiTask(postActions = {"LOG"})} 按名称启用。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiPostTask("LOG")
 * public class LogPostTask implements PostTaskExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiPostTask {

    /**
     * 后置任务类型标识，如 "LOG"、"NOTIFY"。
     * 同时作为 Spring Bean 的名称。
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();
}
