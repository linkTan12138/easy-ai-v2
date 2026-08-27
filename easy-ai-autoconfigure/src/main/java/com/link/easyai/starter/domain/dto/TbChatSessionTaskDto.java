package com.link.easyai.starter.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class TbChatSessionTaskDto {
    private Long id;
    private Integer type;
    private List<CollectParamFieldDto> fieldList;
    private String extraContent;
}
