package com.link.easyai.starter.engine.task;

import com.link.easyai.starter.engine.context.ExecuteContext;

/**
 * 后置任务执行器接口。
 * <p>
 * 在主任务执行成功后依次执行，用于日志、通知、审计等。
 * 采用 best-effort 模式，后置任务失败不影响主任务结果。
 * <p>
 * 实现类需标注 {@link AiPostTask} 注解并注册为 Spring Bean。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiPostTask("LOG")
 * public class LogPostTask implements PostTaskExecutor { ... }
 * </pre>
 */
public interface PostTaskExecutor {

    /**
     * 获取后置任务类型标识，如 "LOG"、"NOTIFY"。
     */
    String type();

    /**
     * 执行后置任务。
     *
     * @param context 执行上下文（与主任务相同，附加主任务结果）
     */
    void execute(ExecuteContext context);
}
