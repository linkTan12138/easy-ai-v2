package com.link.easyai.starter.service;

import com.link.easyai.starter.service.impl.llm.DeepSeekService;
import com.link.easyai.starter.service.impl.llm.DoubaoService;
import com.link.easyai.starter.service.impl.llm.KimiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LargeLanguageModelFactory {


    @Autowired
    private KimiService kimiService;
    @Autowired
    private DoubaoService doubaoService;
    @Autowired
    private DeepSeekService deepSeekService;

    // 根据 scenarioCode 获取对应的处理器
    public LargeLanguageModel getLargeLanguageModel(String name) {
        // 这里简单的通过场景代码来返回相应的处理器
        // 如果找不到，抛出异常
        LargeLanguageModel largeLanguageModel = null;
        switch (name) {
            case "kimi":
                largeLanguageModel = kimiService;
                break;
            case "doubao":
                largeLanguageModel = doubaoService;
                break;
            case "deepseek":
                largeLanguageModel = deepSeekService;
                break;
        }
        if (largeLanguageModel == null) {
            throw new IllegalArgumentException("No largeLanguageModel found for scenario code: " + name);
        }
        return largeLanguageModel;
    }
}
