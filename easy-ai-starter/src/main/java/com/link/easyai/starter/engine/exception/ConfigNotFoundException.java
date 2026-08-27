package com.link.easyai.starter.engine.exception;

/**
 * Thrown when a task config is not found or not published.
 */
public class ConfigNotFoundException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public ConfigNotFoundException(String taskType) {
        super("CONFIG_NOT_FOUND",
                "No published config found for task type: " + taskType);
    }

    public ConfigNotFoundException(String taskType, Integer version) {
        super("CONFIG_NOT_FOUND",
                "No config found for task type: " + taskType + ", version: " + version);
    }
}
