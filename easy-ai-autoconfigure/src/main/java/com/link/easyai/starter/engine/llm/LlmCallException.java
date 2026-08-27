package com.link.easyai.starter.engine.llm;

/**
 * Exception thrown when an LLM call fails.
 * <p>
 * Carries a {@code nonRetryable} flag so the caller knows whether to retry
 * or fail fast. Non-retryable errors are client-side issues (bad prompt,
 * auth failure, invalid request) where retrying won't help.
 */
public class LlmCallException extends RuntimeException {

    private final boolean nonRetryable;

    public LlmCallException(String message, boolean nonRetryable) {
        super(message);
        this.nonRetryable = nonRetryable;
    }

    public LlmCallException(String message, Throwable cause, boolean nonRetryable) {
        super(message, cause);
        this.nonRetryable = nonRetryable;
    }

    public boolean isNonRetryable() {
        return nonRetryable;
    }

    public boolean isRetryable() {
        return !nonRetryable;
    }
}
