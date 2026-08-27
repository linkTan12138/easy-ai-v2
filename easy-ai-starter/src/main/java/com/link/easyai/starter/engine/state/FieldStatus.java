package com.link.easyai.starter.engine.state;

/**
 * Status of a single field in a task.
 * <p>
 * Lifecycle:
 * <pre>
 * PENDING -> EXTRACTED -> VALID -> CONFIRMED
 *                     -> INVALID -> (re-ask user) -> EXTRACTED
 * PENDING -> SKIPPED  (when premise is not met and field is optional)
 * </pre>
 */
public enum FieldStatus {

    /** Field not yet collected */
    PENDING,

    /** LLM has extracted a raw value, but not yet validated */
    EXTRACTED,

    /** Validation failed */
    INVALID,

    /** Validation passed */
    VALID,

    /** User confirmed the value */
    CONFIRMED,

    /** Field skipped (premise not met, or explicitly skipped) */
    SKIPPED
}
