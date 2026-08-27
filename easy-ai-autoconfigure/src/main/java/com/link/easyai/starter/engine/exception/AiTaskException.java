package com.link.easyai.starter.engine.exception;

/**
 * Base runtime exception for all AI Task Engine errors.
 * All engine-specific exceptions extend this class.
 */
public class AiTaskException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Machine-readable error code */
    private final String errorCode;

    public AiTaskException(String message) {
        super(message);
        this.errorCode = "AI_TASK_ERROR";
    }

    public AiTaskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiTaskException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
