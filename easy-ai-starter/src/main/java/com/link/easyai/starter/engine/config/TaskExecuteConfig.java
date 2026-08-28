package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 任务执行器配置。
 * <p>
 * {@code type} 映射到已注册的 {@link com.link.easyai.starter.engine.task.TaskExecutor}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecuteConfig {

    /** 主任务类型标识，如 "CREATE_TICKET" */
    private String type;

    /** 主任务成功后执行的后置任务列表 */
    private List<String> postActions;

    /** 任务的额外参数 */
    private Map<String, Object> params;
}
