package com.link.easyai.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.link.easyai.starter.domain.entity.AiChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * AI 会话状态 Mapper。
 * <p>
 * 会话的隔离维度为 (tenant_id, session_id) 复合键：
 * 同一 sessionId 在不同租户下是相互独立的会话。
 */
@Mapper
public interface AiChatSessionMapper extends BaseMapper<AiChatSession> {

    /**
     * 按 (tenant_id, session_id) 复合键查询会话。
     */
    @Select("SELECT * FROM ai_chat_session WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    AiChatSession selectByTenantAndSession(@Param("tenantId") String tenantId,
                                           @Param("sessionId") String sessionId);

    /**
     * 更新会话的最后活跃时间和轮次计数。
     */
    @Update("UPDATE ai_chat_session SET last_active_time = NOW(), turn_count = turn_count + 1, " +
            "update_time = NOW() WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int touch(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 绑定任务到会话。
     */
    @Update("UPDATE ai_chat_session SET current_task_id = #{taskId}, current_task_type = #{taskType}, " +
            "status = 1, last_active_time = NOW(), turn_count = 0, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int bindTask(@Param("sessionId") String sessionId,
                 @Param("taskId") String taskId,
                 @Param("taskType") String taskType,
                 @Param("tenantId") String tenantId);

    /**
     * 清除会话的当前任务（任务完成/取消/切换时调用）。
     */
    @Update("UPDATE ai_chat_session SET current_task_id = NULL, current_task_type = NULL, " +
            "status = 0, turn_count = 0, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int clearTask(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 标记会话为已过期。
     */
    @Update("UPDATE ai_chat_session SET status = 2, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int markExpired(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 重置会话为空闲状态（超时后复用：清除任务绑定、恢复 IDLE、刷新活跃时间、清零轮次）。
     */
    @Update("UPDATE ai_chat_session SET current_task_id = NULL, current_task_type = NULL, " +
            "status = 0, last_active_time = NOW(), turn_count = 0, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int reset(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 更新会话的对话历史。
     */
    @Update("UPDATE ai_chat_session SET chat_history = #{chatHistory}, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int updateChatHistory(@Param("sessionId") String sessionId,
                          @Param("chatHistory") String chatHistory,
                          @Param("tenantId") String tenantId);

    /**
     * 清空会话的对话历史（任务完成/取消/会话重置时调用）。
     */
    @Update("UPDATE ai_chat_session SET chat_history = NULL, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId}")
    int clearChatHistory(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);
}
