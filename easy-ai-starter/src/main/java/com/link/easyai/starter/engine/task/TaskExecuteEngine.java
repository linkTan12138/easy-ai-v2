package com.link.easyai.starter.engine.task;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.ExecuteContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;

import java.util.Map;

/**
 * 任务执行引擎接口。
 * <p>
 * 编排任务执行流程：
 * 1. 按配置查找主任务执行器
 * 2. 构建执行上下文并执行
 * 3. 成功后依次执行后置任务
 * <p>
 * 主任务失败时，后置任务不执行。
 */
public interface TaskExecuteEngine {

    /**
     * 执行任务。
     *
     * @param config      任务配置
     * @param state       最终任务状态
     * @param parameters  组装后的参数（从字段映射）
     * @param taskContext 任务上下文
     * @return 执行结果
     */
    TaskResult execute(AiTaskConfig config,
                       TaskState state,
                       Map<String, Object> parameters,
                       TaskContext taskContext);
}
