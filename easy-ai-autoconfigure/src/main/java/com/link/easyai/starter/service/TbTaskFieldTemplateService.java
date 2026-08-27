package com.link.easyai.starter.service;


import com.link.easyai.starter.domain.entity.TbTaskFieldTemplate;
import com.link.easyai.starter.domain.vo.TbChatRecordVo;

import java.util.List;

public interface TbTaskFieldTemplateService {

    List<TbChatRecordVo> listChatRecord();

    TbTaskFieldTemplate findByScenarioCode(Integer scenarioCode);

    List<TbTaskFieldTemplate> listForEnable();
}
