package com.link.easyai.starter.domain.vo;

import com.link.easyai.starter.domain.enums.TaskStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseVo {
    private Long taskId;
    private String message;
    private TaskStatusEnum status;

    public static AiChatResponseVo build() {
        AiChatResponseVo aiChatResponseVo = new AiChatResponseVo();
        return aiChatResponseVo;
    }

    public AiChatResponseVo taskId(Long taskId) {
        this.taskId = taskId;
        return this;
    }

    public AiChatResponseVo message(String message) {
        this.message = message;
        return this;
    }

    public AiChatResponseVo status(TaskStatusEnum status) {
        this.status = status;
        return this;
    }
}
