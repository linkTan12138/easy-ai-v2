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
 */
@Mapper
public interface AiChatMessageMapper extends BaseMapper<AiChatMessage> {

    /**
     * 查询会话的最近 N 条消息（按创建时间升序，用于 LLM 上下文）。
     */
    @Select("SELECT * FROM ai_chat_message WHERE session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<AiChatMessage> selectRecentBySessionId(@Param("sessionId") String sessionId,
                                                @Param("limit") int limit);

    /**
     * 查询会话的所有消息（按创建时间升序，用于历史查询）。
     */
    @Select("SELECT * FROM ai_chat_message WHERE session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time ASC")
    List<AiChatMessage> selectAllBySessionId(@Param("sessionId") String sessionId);

    /**
     * 分页查询会话消息。
     */
    @Select("SELECT * FROM ai_chat_message WHERE session_id = #{sessionId} AND deleted = 0 " +
            "ORDER BY create_time ASC LIMIT #{offset}, #{size}")
    List<AiChatMessage> selectPageBySessionId(@Param("sessionId") String sessionId,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    /**
     * 统计会话消息总数。
     */
    @Select("SELECT COUNT(*) FROM ai_chat_message WHERE session_id = #{sessionId} AND deleted = 0")
    long countBySessionId(@Param("sessionId") String sessionId);

    /**
     * 逻辑删除会话的所有消息（清空历史时调用）。
     */
    @Update("UPDATE ai_chat_message SET deleted = 1, update_time = NOW() " +
            "WHERE session_id = #{sessionId} AND deleted = 0")
    int softDeleteBySessionId(@Param("sessionId") String sessionId);

    /**
     * 获取会话当前最大轮次索引。
     */
    @Select("SELECT COALESCE(MAX(turn_index), 0) FROM ai_chat_message " +
            "WHERE session_id = #{sessionId} AND deleted = 0")
    int selectMaxTurnIndex(@Param("sessionId") String sessionId);
}
