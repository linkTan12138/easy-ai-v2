package com.link.easyai.starter.engine.task.builtin;

import com.link.easyai.starter.engine.context.ExecuteContext;
import com.link.easyai.starter.engine.task.AiPostTask;
import com.link.easyai.starter.engine.task.PostTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置后置任务：主任务成功后写入审计日志。
 * <p>
 * 在配置中通过 {@code postActions: ["LOG"]} 启用。
 * 纯框架级别（无业务依赖），用于生产环境追踪任务完成情况。
 */
@AiPostTask("LOG")
public class LoggingPostTask implements PostTaskExecutor {

    private static final Logger auditLog = LoggerFactory.getLogger("AI_TASK_AUDIT");

    @Override
    public String type() {
        return "LOG";
    }

    @Override
    public void execute(ExecuteContext context) {
        auditLog.info("[Audit] AI task completed: taskType={}, taskId={}, taskStatus={}, parameters={}",
                context.getConfig() != null ? context.getConfig().getTaskType() : null,
                context.getTaskId(),
                context.getState() != null ? context.getState().getStatus() : null,
                context.getParameters());
    }
}
