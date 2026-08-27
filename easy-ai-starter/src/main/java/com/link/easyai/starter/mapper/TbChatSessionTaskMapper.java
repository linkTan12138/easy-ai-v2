package com.link.easyai.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TbChatSessionTaskMapper extends BaseMapper<TbChatSessionTask> {

    /**
     * Optimistic lock update: only updates when version matches.
     * Increments version by 1 on success.
     *
     * @param entity the entity to update (must have id and all fields set)
     * @param expectedVersion the version expected in the database
     * @return number of affected rows (0 means concurrent conflict)
     */
    int updateWithVersion(@Param("entity") TbChatSessionTask entity, @Param("expectedVersion") Integer expectedVersion);

    /**
     * 查询指定租户最近一个未完成的任务（状态=处理中 2），按更新时间倒序取第一条。
     * 用于多轮对话连续性恢复：当 session 中没有活跃任务时，尝试找回上一轮未完成任务。
     */
    @Select("SELECT * FROM tb_chat_session_task WHERE tenant_id = #{tenantId} AND status = 2 AND deleted = 0 " +
            "ORDER BY update_time DESC LIMIT 1")
    TbChatSessionTask selectLatestActiveByTenant(@Param("tenantId") Long tenantId);

    /**
     * 按业务 task_id 查询任务记录（唯一索引）。
     */
    @Select("SELECT * FROM tb_chat_session_task WHERE task_id = #{taskId} AND deleted = 0 LIMIT 1")
    TbChatSessionTask selectByTaskId(@Param("taskId") String taskId);
}
