package com.link.easyai.starter.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.link.easyai.starter.domain.dto.ListTaskFieldTemplateDto;
import com.link.easyai.starter.domain.entity.TbTaskFieldTemplate;
import com.link.easyai.starter.service.impl.TaskTemplateService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/easyai/taskTemplate")
public class TaskTemplateController {

    @Autowired
    private TaskTemplateService taskTemplateService;

    @PostMapping("/add")
    public boolean add(@RequestBody TbTaskFieldTemplate entity) {
        return taskTemplateService.save(entity);
    }

    @PostMapping("/update")
    public boolean update(@RequestBody TbTaskFieldTemplate entity) {
        return taskTemplateService.updateById(entity);
    }

    @PostMapping("/list")
    public Page<TbTaskFieldTemplate> list(@RequestBody ListTaskFieldTemplateDto dto) {
        Page<TbTaskFieldTemplate> page = new Page<>(dto.getCurrentPage(), dto.getPageSize());
        return taskTemplateService.page(page, new LambdaQueryWrapper<TbTaskFieldTemplate>()
                .like(StringUtils.isNotBlank(dto.getKeyword()), TbTaskFieldTemplate::getTemplateName, dto.getKeyword())
                .or()
                .like(StringUtils.isNotBlank(dto.getKeyword()), TbTaskFieldTemplate::getScenarioCode, dto.getKeyword())
                .orderByDesc(TbTaskFieldTemplate::getEnable)
                .orderByAsc(TbTaskFieldTemplate::getScenarioCode));
    }


    @GetMapping("/delete/{id}")
    public boolean delete(@PathVariable Long id) {
        return taskTemplateService.removeById(id);
    }

    @GetMapping("/detail/{id}")
    public TbTaskFieldTemplate detail(@PathVariable Long id) {
        return taskTemplateService.getOne(new LambdaQueryWrapper<TbTaskFieldTemplate>().eq(TbTaskFieldTemplate::getId, id));
    }
}
