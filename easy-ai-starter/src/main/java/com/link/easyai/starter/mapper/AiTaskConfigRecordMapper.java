package com.link.easyai.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link AiTaskConfigRecord} (ai_task_config table).
 */
@Mapper
public interface AiTaskConfigRecordMapper extends BaseMapper<AiTaskConfigRecord> {
}
