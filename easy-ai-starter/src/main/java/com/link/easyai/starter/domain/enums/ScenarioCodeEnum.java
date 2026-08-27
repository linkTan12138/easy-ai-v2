package com.link.easyai.starter.domain.enums;

/**
 * @author :tanfuxing
 * @date :2023/1/31
 * @description :
 */
public enum ScenarioCodeEnum {
    DEFAULT(0,"默认场景"),
    REGISTER(1,"注册账号"),
    RESET_PASSWORD(2,"找回密码"),
    TERMINAL_TASK(100,"终止任务");

    private Integer code;

    private String desc;

    ScenarioCodeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return this.code;
    }

    public String getDesc() {
        return this.desc;
    }
}
