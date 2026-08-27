package com.link.easyai.starter.domain.enums;

/**
 * @author :tanfuxing
 * @date :2023/1/31
 * @description :
 */
public enum StatusCodeEnum {
    SUCCESS(200,"请求成功"),
    FAIL(400,"请求失败"),
    ERROR(500,"系统错误");
    
    private int code;
    
    private String message;
    
    StatusCodeEnum(int code, String message) {
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
