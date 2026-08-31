package com.link.easyai.starter.engine.session;

import com.link.easyai.starter.domain.entity.AiChatSession;

/**
 * 会话状态管理器。
 * <p>
 * 负责维护会话（(tenant_id, session_id) 复合标识）与当前活跃任务的绑定关系，支持：
 * <ul>
 *   <li>加载/创建会话（按租户+会话复合键隔离）</li>
 *   <li>绑定任务到会话</li>
 *   <li>清除会话任务（任务完成/取消）</li>
 *   <li>会话超时判断</li>
 *   <li>更新最后活跃时间（心跳）</li>
 * </ul>
 * 多租户多用户场景下，同一 sessionId 在不同租户内是相互独立的会话，互不串扰。
 */
public interface SessionManager {

    /**
     * 加载会话，如果不存在则创建一个 IDLE 状态的会话。
     * <p>
     * 以 (tenant_id, session_id) 复合键定位：同一 sessionId 在不同租户下视为不同会话。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID（支持数字或字符串编码）
     * @return 会话实体
     */
    AiChatSession loadOrCreate(String sessionId, String tenantId);

    /**
     * 绑定任务到会话（切换到新任务时调用，旧任务由调用方标记 CANCELLED）。
     *
     * @param sessionId 会话 ID
     * @param taskId    任务 ID
     * @param taskType  任务类型
     * @param tenantId  租户 ID（与 sessionId 共同定位会话）
     */
    void bindTask(String sessionId, String taskId, String taskType, String tenantId);

    /**
     * 清除会话的当前任务（任务完成/取消时调用）。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     */
    void clearTask(String sessionId, String tenantId);

    /**
     * 更新会话的最后活跃时间并递增轮次计数。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     */
    void touch(String sessionId, String tenantId);

    /**
     * 判断会话是否已超时。
     *
     * @param session        会话实体
     * @param timeoutMinutes 超时时间（分钟）
     * @return true 表示已超时
     */
    boolean isExpired(AiChatSession session, int timeoutMinutes);

    /**
     * 标记会话为已过期。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     */
    void markExpired(String sessionId, String tenantId);

    /**
     * 重置会话为空闲状态（超时后复用：清除任务绑定、恢复 IDLE、刷新活跃时间、清零轮次）。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     */
    void reset(String sessionId, String tenantId);
}
