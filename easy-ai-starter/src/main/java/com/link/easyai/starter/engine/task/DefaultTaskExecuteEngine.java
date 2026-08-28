package com.link.easyai.starter.engine.task;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.TaskExecuteConfig;
import com.link.easyai.starter.engine.context.ExecuteContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link TaskExecuteEngine} 的默认实现。
 * <p>
 * 执行顺序：
 * <ol>
 *   <li>按 {@code config.executeConfig.type} 在 {@link TaskRegistry} 中查找主任务执行器</li>
 *   <li>构建 {@link ExecuteContext}（参数 + 状态 + 配置 + 额外参数）</li>
 *   <li>执行主任务</li>
 *   <li>仅当主任务成功时，依次执行所有后置任务（{@code config.executeConfig.postActions}）</li>
 * </ol>
 * 后置任务是 best-effort：缺失或失败的后置任务只记录日志，不影响已成功的主任务结果。
 */
@Component
public class DefaultTaskExecuteEngine implements TaskExecuteEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskExecuteEngine.class);

    private final TaskRegistry taskRegistry;

    @Autowired
    public DefaultTaskExecuteEngine(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @Override
    public TaskResult execute(AiTaskConfig config,
                              TaskState state,
                              Map<String, Object> parameters,
                              TaskContext taskContext) {
        TaskExecuteConfig executeConfig = config != null ? config.getExecuteConfig() : null;
        if (executeConfig == null || executeConfig.getType() == null || executeConfig.getType().isBlank()) {
            log.error("[TaskExecuteEngine] no task type configured for taskType={}",
                    config != null ? config.getTaskType() : null);
            return TaskResult.fail("TASK_NOT_CONFIGURED", "任务未配置执行器");
        }

        String type = executeConfig.getType();
        TaskExecutor executor = taskRegistry.getTask(type);
        if (executor == null) {
            log.error("[TaskExecuteEngine] task '{}' not registered", type);
            return TaskResult.fail("TASK_NOT_FOUND", "任务执行器未注册: " + type);
        }

        // 合并配置级别的额外参数（配置参数不会覆盖字段映射的值）
        Map<String, Object> mergedParameters = new HashMap<>();
        if (executeConfig.getParams() != null) {
            mergedParameters.putAll(executeConfig.getParams());
        }
        if (parameters != null) {
            mergedParameters.putAll(parameters);
        }

        ExecuteContext context = ExecuteContext.builder()
                .taskId(state != null ? state.getTaskId() : null)
                .config(config)
                .state(state)
                .parameters(mergedParameters)
                .taskContext(taskContext)
                .build();

        // 执行主任务
        TaskResult result;
        try {
            result = executor.execute(context);
        } catch (Exception e) {
            log.error("[TaskExecuteEngine] task '{}' threw exception", type, e);
            return TaskResult.fail("TASK_ERROR", "任务执行异常: " + e.getMessage());
        }
        if (result == null) {
            log.error("[TaskExecuteEngine] task '{}' returned null", type);
            return TaskResult.fail("TASK_ERROR", "任务执行返回空结果: " + type);
        }

        log.info("[TaskExecuteEngine] task '{}' finished: success={}", type, result.isSuccess());

        // 后置任务仅在主任务成功后执行
        if (result.isSuccess()) {
            executePostTasks(executeConfig, context);
        }
        return result;
    }

    /**
     * 按顺序执行配置的后置任务。Best-effort：失败只记录日志，不影响主任务结果。
     */
    private void executePostTasks(TaskExecuteConfig executeConfig, ExecuteContext context) {
        if (executeConfig.getPostActions() == null || executeConfig.getPostActions().isEmpty()) {
            return;
        }
        for (String postType : executeConfig.getPostActions()) {
            if (postType == null || postType.isBlank()) {
                continue;
            }
            PostTaskExecutor postTask = taskRegistry.getPostTask(postType.trim());
            if (postTask == null) {
                log.warn("[TaskExecuteEngine] post-task '{}' not registered, skipped", postType);
                continue;
            }
            try {
                postTask.execute(context);
                log.debug("[TaskExecuteEngine] post-task '{}' done", postType);
            } catch (Exception e) {
                log.error("[TaskExecuteEngine] post-task '{}' failed (main task already succeeded, "
                        + "continuing)", postType, e);
            }
        }
    }
}
