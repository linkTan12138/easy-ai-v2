package com.link.easyai.starter.engine.task.builtin;

import com.link.easyai.starter.engine.context.ExecuteContext;
import com.link.easyai.starter.engine.task.AiTask;
import com.link.easyai.starter.engine.task.TaskExecutor;
import com.link.easyai.starter.engine.task.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 内置演示任务：回显已收集的参数作为成功结果。
 * <p>
 * 仅用于测试和接线验证，业务项目应注册自己的 {@code @AiTask} Bean。
 */
@AiTask(value = "ECHO",
        name = "回显演示",
        description = "内置演示动作，回显已收集的参数。仅用于测试和接线验证，不面向终端用户展示。",
        hidden = true)
public class EchoTask implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(EchoTask.class);

    @Override
    public String type() {
        return "ECHO";
    }

    @Override
    public TaskResult execute(ExecuteContext context) {
        Map<String, Object> params = context.getParameters();
        log.info("[EchoTask] executing with parameters: {}", params);

        String summary = "已收集参数: " + params;
        return TaskResult.success("演示动作执行成功！" + summary, params);
    }
}
