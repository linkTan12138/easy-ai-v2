package com.link.easyai.starter.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ai_chat_session_task")
public class TbChatSessionTask extends BaseEntity {

    private Long id;
    /** AI Task Engine: 业务任务ID（任意字符串），唯一索引，替代用 id 作为 taskId 的脆弱设计 */
    private String taskId;
    private Integer type;
    private String fieldList;
    // 历史会话记录
    private String records;
    private String extraContent;
    // 状态: 0-待处理 1-待唤醒 2-处理中 3-失败 4-已停止 5-已完成
    private Integer status;
    // 场景: 0：无场景匹配 1：注册账号 2:写博客
    private Integer scenarioCode;
    // AI Task Engine: task type, e.g. "ORDER_UPDATE"
    private String taskType;
    // AI Task Engine: bound config version
    private Integer configVersion;
    // AI Task Engine: full task state serialized as JSON
    private String aiTaskState;
    // 意图识别：判断理由（用于调试提示词）
    private String intentReason;
    // 意图识别：置信度 0.0-1.0
    private Double intentConfidence;
    // 意图识别：匹配来源（LLM / KEYWORD / FALLBACK / CONTINUE）
    private String intentSource;
    // Optimistic lock version
    private Integer version;
    /** 租户 ID（支持数字或字符串编码） */
    private String tenantId;

}
