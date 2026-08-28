package com.link.easyai.starter.engine.context;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.state.TaskState;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * 任务执行时的上下文。
 * <p>
 * 传递给 {@link com.link.easyai.starter.engine.task.TaskExecutor} 的执行上下文，
 * 包含组装后的参数、任务状态、任务配置等。
 * <p>
 * 注意：与 {@link TaskContext} 不同，{@code TaskContext} 是请求级别的上下文
 * （tenantId、userDetails 等），而本类是执行时的上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteContext {

    /** 任务 ID */
    private String taskId;

    /** 驱动此任务的配置 */
    private AiTaskConfig config;

    /** 最终的任务状态 */
    private TaskState state;

    /** 从字段映射组装的参数，如 {"ticketType": "COMPLAINT"} */
    private Map<String, Object> parameters;

    /** 原始的任务上下文（tenant、user 等） */
    private TaskContext taskContext;

    /** 业务上下文对象（如加载的订单实体） */
    private Object businessContext;
}
