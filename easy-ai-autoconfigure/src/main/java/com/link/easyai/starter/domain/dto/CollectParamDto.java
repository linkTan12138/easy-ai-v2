package com.link.easyai.starter.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CollectParamDto {
    private Long taskId;
    private String message;

    public static CollectParamDto build(Long taskId) {
        CollectParamDto collectParamDto = new CollectParamDto();
        collectParamDto.setTaskId(taskId);
        return collectParamDto;
    }

    public CollectParamDto message(String message) {
        this.message = message;
        return this;
    }
}
