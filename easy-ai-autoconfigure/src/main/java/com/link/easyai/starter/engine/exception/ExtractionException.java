package com.link.easyai.starter.engine.exception;

/**
 * Thrown when LLM extraction fails (invalid JSON, LLM error, timeout, etc.)
 */
public class ExtractionException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public ExtractionException(String message) {
        super("EXTRACTION_ERROR", message);
    }

    public ExtractionException(String message, Throwable cause) {
        super("EXTRACTION_ERROR", message, cause);
    }
}
