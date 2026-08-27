package com.link.easyai.starter.service;

import cn.hutool.extra.spring.SpringUtil;
import com.link.easyai.starter.domain.dto.AiSceneProcessorDto;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.domain.vo.AiChatResponseVo;
import com.link.easyai.starter.domain.vo.CollectFieldValidVo;
import com.link.easyai.starter.mapper.TbChatSessionTaskMapper;
import com.link.easyai.starter.service.AiSceneProcessor;
import com.link.easyai.starter.service.LargeLanguageModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public abstract class AbstractAiSceneProcessor implements AiSceneProcessor {

    @Autowired
    private TbChatSessionTaskMapper tbChatSessionTaskMapper;

    private LargeLanguageModel largeLanguageModel;

    @Override
    public LargeLanguageModel getLargeLanguageModel() {
        return this.largeLanguageModel;
    }

    @Override
    public TbChatSessionTaskMapper getTaskMapper() {
        return tbChatSessionTaskMapper;
    }

    @Override
    public AiChatResponseVo process(AiSceneProcessorDto processorDto) throws Exception {
        largeLanguageModel = processorDto.getLargeLanguageModel();
        TbChatSessionTask task = getTaskMapper().selectById(processorDto.getTaskId());
        processorDto.setTask(task);
        return doProcess(processorDto);
    }

    public abstract AiChatResponseVo doProcess(AiSceneProcessorDto processorDto) throws Exception;

    @Override
    public String transScenarioMsg() {
        return "";
    }

    @Override
    public CollectFieldValidVo validField(String field, Object value) {
        switch (field) {
            case "email":
                if(validEmail(String.valueOf(value))) return CollectFieldValidVo.build(true);
                return CollectFieldValidVo.build(false).errMsg("邮箱格式不正确。");
            case "password":
                if(validPassWord(String.valueOf(value))) return CollectFieldValidVo.build(true);
                return CollectFieldValidVo.build(false).errMsg("密码至少包含一个字母、一个数字，且长度在8到20之间");
        }
        return CollectFieldValidVo.build(true);
    }

    private final String PASSWORD_PATTERN = "^(?=(.*[a-zA-Z]))(?=(.*\\d))[\\w!@#$%^&*()_+={}\\[\\]:;\"'<>,.?/\\\\|-]{8,20}$";

    private final String EMAIL_PATTERN = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";



    public Boolean validPassWord(String password) {
        if (!password.matches(PASSWORD_PATTERN)) {
            return false;
        }
        return true;
    }

    public Boolean validEmail(String email) {
        if (StringUtils.isBlank(email) || !email.matches(EMAIL_PATTERN)) {
            return false;
        }
        return true;
    }

}
