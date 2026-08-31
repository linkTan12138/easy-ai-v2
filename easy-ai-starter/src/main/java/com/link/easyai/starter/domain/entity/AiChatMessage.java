package com.link.easyai.starter.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体。
 * <p>
 * 独立存储每条对话消息，替代 ai_chat_session.chat_history JSON 字段，
 * 支持历史消息查询、分页、按任务筛选等场景。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 消息角色：user / assistant / system */
    private String role;

    /** 消息内容 */
    private String content;

    /** 关联的任务 ID（可选） */
    private String taskId;

    /** 任务类型（可选，冗余便于查询） */
    private String taskType;

    /** 轮次索引，同一 session 内递增 */
    private Integer turnIndex;

    /** 租户 ID（支持数字或字符串编码） */
    private String tenantId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    private Integer deleted;
}
