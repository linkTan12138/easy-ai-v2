package com.link.easyai.starter.engine.task;

import com.link.easyai.starter.engine.context.ExecuteContext;

/**
 * AI 任务执行器接口。
 * <p>
 * 实现类需标注 {@link AiTask} 注解并注册为 Spring Bean。
 * 当任务参数收集完成（或纯动作场景直接触发）时，由任务引擎调用执行。
 * <p>
 * 示例：
 * <pre>
 * &#64;AiTask("CREATE_TICKET")
 * public class CreateTicketTask implements TaskExecutor { ... }
 * </pre>
 */
public interface TaskExecutor {

    /**
     * 获取任务类型标识，如 "CREATE_TICKET"。
     * 必须与 {@link AiTask#value()} 一致。
     */
    String type();

    /**
     * 执行业务任务。
     *
     * @param context 执行上下文（参数、状态、配置等）
     * @return 执行结果
     */
    TaskResult execute(ExecuteContext context);
}
