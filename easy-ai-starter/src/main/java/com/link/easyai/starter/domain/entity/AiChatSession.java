package com.link.easyai.starter.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话状态实体。
 * <p>
 * 维护 sessionId 与当前活跃任务的绑定关系，支持：
 * <ul>
 *   <li>多轮对话连续性（同一 sessionId 延续同一任务）</li>
 *   <li>任务切换（旧任务取消，绑定新任务）</li>
 *   <li>会话超时（last_active_time 超过阈值标记 EXPIRED）</li>
 * </ul>
 */
@Data
public class AiChatSession {

    /** 自增主键（逻辑主键，会话唯一标识为 tenant_id + session_id 复合键） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID（业务标识，同一 sessionId 在不同租户下相互独立） */
    private String sessionId;

    /** 当前活跃任务 ID，NULL 表示无活跃任务 */
    private String currentTaskId;

    /** 当前任务类型，冗余便于查询 */
    private String currentTaskType;

    /** 状态: 0-IDLE, 1-ACTIVE, 2-EXPIRED */
    private Integer status;

    /** 最后活跃时间，用于超时判断 */
    private LocalDateTime lastActiveTime;

    /** 当前任务已进行轮次 */
    private Integer turnCount;

    /** 租户 ID（支持数字或字符串编码），与 sessionId 共同构成会话隔离维度 */
    private String tenantId;

    /**
     * 对话历史（JSON数组），滑动窗口保留最近N轮对话。
     * 用于传递给LLM提升多轮上下文理解能力。
     */
    private String chatHistory;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    // ---- 状态常量 ----
    public static final int STATUS_IDLE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_EXPIRED = 2;
}
