package com.link.easyai.starter.engine.action;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Result of an action execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {

    /** Whether the action succeeded */
    private boolean success;

    /** Human-readable result message (shown to user) */
    private String message;

    /** Business data returned by the action (e.g. updated order ID) */
    private Object data;

    /** Error code if action failed */
    private String errorCode;

    /** Error message if action failed */
    private String errorMessage;

    /**
     * Create a success result.
     */
    public static ActionResult success(String message, Object data) {
        return ActionResult.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Create a failure result.
     */
    public static ActionResult fail(String errorCode, String errorMessage) {
        return ActionResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
