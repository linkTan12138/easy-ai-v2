package com.link.easyai.starter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.link.easyai.starter.domain.entity.TbChatRecord;
import com.link.easyai.starter.domain.vo.TbChatRecordVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TbChatRecordMapper extends BaseMapper<TbChatRecord> {
    List<TbChatRecordVo> listChatRecord();

    List<TbChatRecordVo> listChatRecordGroupTenant();
}
