package com.link.easyai.starter.domain.entity;

import lombok.Data;

@Data
public class TbTaskFieldTemplate extends BaseEntity {

    private Long id;
    private String templateName;
    private String description;
    private String fieldList;
    // 场景: 0：无场景匹配 1：注册账号 2:写博客
    private Integer scenarioCode;
    // 状态: 0:禁用 1:启用
    private Integer enable;
    private Long tenantId;

}
