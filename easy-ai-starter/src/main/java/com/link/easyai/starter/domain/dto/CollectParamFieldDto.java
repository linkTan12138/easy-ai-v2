package com.link.easyai.starter.domain.dto;

import lombok.Data;

@Data
public class CollectParamFieldDto {
    private String field;
    private String premiseFields;
    private String fieldName;
    private String fieldType;
    private String judgmentLogic;
    private String example;
    private String enums;
    // 是否敏感：1是 0否
    private Integer sensitive;
    private Integer required;
}
