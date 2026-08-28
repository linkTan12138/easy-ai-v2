package com.link.easyai.starter.engine.task;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 任务执行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {

    /** 任务是否执行成功 */
    private boolean success;

    /** 给用户看的结果消息 */
    private String message;

    /** 任务返回的业务数据（如创建的工单ID） */
    private Object data;

    /** 失败时的错误码 */
    private String errorCode;

    /** 失败时的错误消息 */
    private String errorMessage;

    /**
     * 创建成功结果。
     */
    public static TaskResult success(String message, Object data) {
        return TaskResult.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 创建失败结果。
     */
    public static TaskResult fail(String errorCode, String errorMessage) {
        return TaskResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
