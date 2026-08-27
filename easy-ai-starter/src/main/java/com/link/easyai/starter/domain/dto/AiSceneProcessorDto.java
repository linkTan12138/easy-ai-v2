package com.link.easyai.starter.domain.dto;

import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.service.LargeLanguageModel;
import lombok.Data;

@Data
public class AiSceneProcessorDto {
    private Long taskId;
    private String message;
    private LargeLanguageModel largeLanguageModel;
    private TbChatSessionTask task;

    public static AiSceneProcessorDto build() {
        AiSceneProcessorDto dto = new AiSceneProcessorDto();
        return dto;
    }

    public AiSceneProcessorDto setTaskId(Long taskId) {
        this.taskId = taskId;
        return this;
    }

    public AiSceneProcessorDto setMessage(String message) {
        this.message = message;
        return this;
    }

    public AiSceneProcessorDto setLargeLanguageModel(LargeLanguageModel largeLanguageModel) {
        this.largeLanguageModel = largeLanguageModel;
        return this;
    }
}
