package com.link.easyai.starter.engine.exception;

/**
 * 当任务执行器未在注册表中找到时抛出。
 */
public class TaskNotFoundException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public TaskNotFoundException(String taskType) {
        super("TASK_NOT_FOUND",
                "Task not found in registry: " + taskType +
                ". Make sure the task is annotated with @AiTask and registered as a Spring bean.");
    }
}
