package com.link.easyai.starter.config;

import com.link.easyai.starter.llm.LLMProvider;
import com.link.easyai.starter.llm.LLMProviderFactory;

/**
 * LLM Provider 持有者。
 * <p>
 * 在启动时根据配置的 active provider 创建并缓存默认的 {@link LLMProvider} 实例。
 */
public class LLMlHolder {

    private final LLMProvider llmProvider;
    private final String activeModelName;

    public LLMlHolder(LLMProviderFactory llmProviderFactory, String active) {
        this.llmProvider = llmProviderFactory.create(active);
        this.activeModelName = active;
    }

    public LLMProvider getLlmProvider() {
        return llmProvider;
    }

    public String getActiveModelName() {
        return activeModelName;
    }
}
