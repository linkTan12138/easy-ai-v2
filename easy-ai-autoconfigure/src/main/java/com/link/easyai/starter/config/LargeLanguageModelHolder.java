package com.link.easyai.starter.config;

import com.link.easyai.starter.service.LargeLanguageModel;
import com.link.easyai.starter.service.LargeLanguageModelFactory;

public class LargeLanguageModelHolder {

    private final LargeLanguageModel largeLanguageModel;
    private final String activeModelName;

    public LargeLanguageModelHolder(LargeLanguageModelFactory largeLanguageModelFactory, String active) {
        this.largeLanguageModel = largeLanguageModelFactory.getLargeLanguageModel(active);
        this.activeModelName = active;
    }

    public LargeLanguageModel getLargeLanguageModel() {
        return largeLanguageModel;
    }

    public String getActiveModelName() {
        return activeModelName;
    }

}
