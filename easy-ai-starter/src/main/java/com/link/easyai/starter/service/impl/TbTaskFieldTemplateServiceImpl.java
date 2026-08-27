package com.link.easyai.starter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.link.easyai.starter.domain.entity.TbTaskFieldTemplate;
import com.link.easyai.starter.domain.vo.TbChatRecordVo;
import com.link.easyai.starter.mapper.TbChatRecordMapper;
import com.link.easyai.starter.mapper.TbTaskFieldTemplateMapper;
import com.link.easyai.starter.service.TbTaskFieldTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class TbTaskFieldTemplateServiceImpl implements TbTaskFieldTemplateService {

    @Autowired
    private TbChatRecordMapper tbChatRecordMapper;
    @Autowired
    private TbTaskFieldTemplateMapper tbTaskFieldTemplateMapper;

    @Override
    public List<TbChatRecordVo> listChatRecord() {
        return tbChatRecordMapper.listChatRecord();
    }


    @Override
    public TbTaskFieldTemplate findByScenarioCode(Integer scenarioCode) {
        TbTaskFieldTemplate tbTaskFieldTemplate = tbTaskFieldTemplateMapper.selectOne(new LambdaQueryWrapper<TbTaskFieldTemplate>()
                .eq(TbTaskFieldTemplate::getScenarioCode, scenarioCode)
                .eq(TbTaskFieldTemplate::getEnable, 1));
        return tbTaskFieldTemplate;
    }

    @Override
    public List<TbTaskFieldTemplate> listForEnable() {
        List<TbTaskFieldTemplate> tbTaskFieldTemplates = tbTaskFieldTemplateMapper.selectList(new LambdaQueryWrapper<TbTaskFieldTemplate>()
                .eq(TbTaskFieldTemplate::getEnable, 1));
        return tbTaskFieldTemplates;
    }
}
