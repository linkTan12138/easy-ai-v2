package com.link.easyai.starter.engine.exception;

/**
 * Thrown when a task config fails validation (duplicate fields, unknown validator, etc.)
 */
public class ConfigValidationException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public ConfigValidationException(String message) {
        super("CONFIG_VALIDATION_ERROR", message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super("CONFIG_VALIDATION_ERROR", message, cause);
    }
}
