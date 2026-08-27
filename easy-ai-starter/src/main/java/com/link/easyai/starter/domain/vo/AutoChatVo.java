package com.link.easyai.starter.domain.vo;

import com.link.easyai.starter.engine.ChatResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 自动意图识别聊天响应 VO。
 * <p>
 * 包装 {@link ChatResponse}，对外暴露意图识别结果和任务执行状态。
 * 与 {@link EngineChatVo} 不同，此 VO 包含意图识别相关字段（taskType、
 * clarification），因为调用方不需要预先知道任务类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoChatVo {

    /** 用户可见的回复消息 */
    private String message;

    /** 任务 ID（未匹配到任务时为 null） */
    private String taskId;

    /** 匹配到的任务类型 / 意图（兜底或澄清时可能为 null） */
    private String taskType;

    /** 任务是否已完成（动作执行成功） */
    private boolean completed;

    /** 是否需要用户提供更多信息 */
    private boolean needMore;

    /** 是否是澄清请求（意图低置信度，需要用户确认选择哪个任务） */
    private boolean clarification;

    /** 动作执行结果数据（任务完成时返回） */
    private Object actionData;

    /** 当前任务状态，如 COLLECTING、READY、EXECUTING、COMPLETED */
    private String status;

    /**
     * 从 ChatResponse 转换为 VO。
     */
    public static AutoChatVo from(ChatResponse response) {
        AutoChatVo vo = new AutoChatVo();
        vo.setMessage(response.getMessage());
        vo.setTaskId(response.getTaskId());
        vo.setTaskType(response.getTaskType());
        vo.setCompleted(response.isCompleted());
        vo.setNeedMore(response.isNeedMore());
        vo.setClarification(response.isClarification());
        if (response.getActionResult() != null) {
            vo.setActionData(response.getActionResult().getData());
        }
        if (response.getTaskState() != null && response.getTaskState().getStatus() != null) {
            vo.setStatus(response.getTaskState().getStatus().name());
        }
        return vo;
    }
}
