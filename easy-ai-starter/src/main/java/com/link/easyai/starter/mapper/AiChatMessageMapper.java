package com.link.easyai.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.link.easyai.starter.domain.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 对话消息 Mapper。
 * <p>
 * 消息隔离维度为 (tenant_id, session_id)：同一 sessionId 在不同租户下互不干扰。
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 查询会话的最近 N 条消息（按创建时间升序，用于 LLM 上下文）。
     */
    @Select("SELECT * FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<AiChatMessage> selectRecentBySessionId(@Param("sessionId") String sessionId,
                                                @Param("tenantId") String tenantId,
                                                @Param("limit") int limit);

    /**
     * 查询指定任务的最近 N 条消息（按创建时间升序，用于当前任务的参数抽取上下文）。
     * 只返回 task_id 匹配的消息，避免跨任务历史污染。
     */
    @Select("SELECT * FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND task_id = #{taskId} AND deleted = 0 " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<AiChatMessage> selectBySessionIdAndTaskId(@Param("sessionId") String sessionId,
                                                   @Param("taskId") String taskId,
                                                   @Param("tenantId") String tenantId,
                                                   @Param("limit") int limit);

    /**
     * 查询会话的所有消息（按创建时间升序，用于历史查询）。
     */
    @Select("SELECT * FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time ASC")
    List<AiChatMessage> selectAllBySessionId(@Param("sessionId") String sessionId,
                                             @Param("tenantId") String tenantId);

    /**
     * 分页查询会话消息。
     */
    @Select("SELECT * FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time ASC LIMIT #{offset}, #{size}")
    List<AiChatMessage> selectPageBySessionId(@Param("sessionId") String sessionId,
                                              @Param("tenantId") String tenantId,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /**
     * 统计会话消息总数。
     */
    @Select("SELECT COUNT(*) FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0")
    long countBySessionId(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 逻辑删除会话的所有消息（清空历史时调用）。
     */
    @Update("UPDATE ai_chat_message SET deleted = 1, update_time = NOW() " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0")
    int softDeleteBySessionId(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 获取会话当前最大轮次索引。
     */
    @Select("SELECT COALESCE(MAX(turn_index), 0) FROM ai_chat_message " +
            "WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0")
    int selectMaxTurnIndex(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);

    /**
     * 查询指定会话最近一条关联了任务的消息的 task_id（按 turn_index 倒序）。
     * 用于多租户多用户场景下，按会话（而非租户）找回该用户最近的任务。
     *
     * @param sessionId 会话 ID
     * @param tenantId  租户 ID
     * @return 最近关联的 task_id；若该会话从未关联过任务或消息均被清空，返回 null
     */
    @Select("SELECT task_id FROM ai_chat_message WHERE tenant_id = #{tenantId} AND session_id = #{sessionId} AND deleted = 0 " +
            "AND task_id IS NOT NULL AND task_id != '' ORDER BY turn_index DESC LIMIT 1")
    String selectLatestTaskIdBySession(@Param("sessionId") String sessionId, @Param("tenantId") String tenantId);
}
