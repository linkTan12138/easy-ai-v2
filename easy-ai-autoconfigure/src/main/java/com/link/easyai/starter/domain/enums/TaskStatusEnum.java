package com.link.easyai.starter.domain.enums;

/**
 * @author :tanfuxing
 * @date :2023/1/31
 * @description : 任务状态枚举
 */
public enum TaskStatusEnum {

    PENDING(0, "待处理"),
    WAITING(1, "待唤醒"),
    PROCESSING(2, "处理中"),
    FAILED(3, "失败"),
    STOPPED(4, "已停止"),
    COMPLETED(5, "已完成");

    private int code;
    private String message;

    TaskStatusEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }
}
