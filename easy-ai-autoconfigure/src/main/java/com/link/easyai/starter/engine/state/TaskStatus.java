package com.link.easyai.starter.engine.state;

/**
 * Overall status of an AI Task.
 * <p>
 * This is the engine-level task status, separate from the existing
 * {@link com.link.easyai.starter.domain.enums.TaskStatusEnum} which tracks
 * the chat session task lifecycle. The engine uses this internally.
 */
public enum TaskStatus {

    /** Task created, no fields collected yet */
    INITIALIZED,

    /** Task is collecting fields */
    COLLECTING,

    /** All required fields collected, ready for action */
    READY,

    /** Action is being executed */
    EXECUTING,

    /** Action executed successfully */
    COMPLETED,

    /** Task failed */
    FAILED,

    /** Task cancelled by user or system */
    CANCELLED,

    /** Task expired due to timeout */
    EXPIRED
}
