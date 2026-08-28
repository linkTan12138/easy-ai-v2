package com.link.easyai.starter.domain.vo;

import com.link.easyai.starter.engine.AiTaskResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Response VO for the AI Task Engine chat endpoint.
 * <p>
 * Wraps {@link AiTaskResponse} in a front-end friendly structure.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineChatVo {

    /** Task ID */
    private String taskId;

    /** Whether the task is complete (action executed) */
    private boolean completed;

    /** Message to show the user */
    private String message;

    /** Action result data if the task completed */
    private Object actionData;

    /** Current task status, e.g. COLLECTING, READY, EXECUTING, COMPLETED */
    private String status;

    /**
     * Convert from AiTaskResponse to VO.
     */
    public static EngineChatVo from(AiTaskResponse response) {
        EngineChatVo vo = new EngineChatVo();
        vo.setTaskId(response.getTaskId());
        vo.setCompleted(response.isCompleted());
        vo.setMessage(response.getMessage());
        if (response.getTaskResult() != null) {
            vo.setActionData(response.getTaskResult().getData());
        }
        if (response.getState() != null && response.getState().getStatus() != null) {
            vo.setStatus(response.getState().getStatus().name());
        }
        return vo;
    }
}
