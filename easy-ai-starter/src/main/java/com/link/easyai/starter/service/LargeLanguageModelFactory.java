package com.link.easyai.starter.service;

import com.link.easyai.starter.llm.LLMConfig;
import com.link.easyai.starter.llm.LLMProvider;
import com.link.easyai.starter.llm.LLMProviderAdapter;
import com.link.easyai.starter.llm.LLMProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 大模型工厂（兼容层）。
 * <p>
 * 已重构为委托给新的 {@link LLMProviderFactory} SPI 工厂，支持：
 * <ul>
 *   <li>内置短名：kimi / deepseek / doubao / openai_compatible</li>
 *   <li>客户自定义：完整类名反射加载</li>
 *   <li>SPI 自动发现：按 getName() 短名查找</li>
 * </ul>
 * 对外保持 {@link #getLargeLanguageModel(String)} 方法签名不变，
 * 返回的 {@link LargeLanguageModel} 实际为 {@link LLMProviderAdapter}。
 */
@Service
public class LargeLanguageModelFactory {

    private static final Logger log = LoggerFactory.getLogger(LargeLanguageModelFactory.class);

    private final LLMProviderFactory llmProviderFactory;
    private final LLMConfig llmConfig;

    /** 适配器缓存：providerName -> LargeLanguageModel */
    private final ConcurrentHashMap<String, LargeLanguageModel> adapterCache = new ConcurrentHashMap<>();

    public LargeLanguageModelFactory(LLMProviderFactory llmProviderFactory, LLMConfig llmConfig) {
        this.llmProviderFactory = llmProviderFactory;
        this.llmConfig = llmConfig;
    }

    /**
     * 根据名称获取对应的大模型处理器。
     *
     * @param name provider 短名（kimi/deepseek/doubao/openai_compatible）、
     *             SPI 注册名、或客户自定义实现类完整类名
     * @return LargeLanguageModel 实例
     * @throws IllegalArgumentException 如果找不到对应的 provider
     */
    public LargeLanguageModel getLargeLanguageModel(String name) {
        return adapterCache.computeIfAbsent(name, this::buildAdapter);
    }

    private LargeLanguageModel buildAdapter(String name) {
        log.info("[LargeLanguageModelFactory] resolving provider '{}'", name);
        LLMProvider provider = llmProviderFactory.create(name);
        LLMProviderAdapter adapter = new LLMProviderAdapter(provider, llmConfig);
        log.info("[LargeLanguageModelFactory] provider '{}' resolved to {}", name, provider.getClass().getName());
        return adapter;
    }

    /**
     * 获取底层 SPI 工厂（供需要直接使用 LLMProvider 的场景）。
     */
    public LLMProviderFactory getLlmProviderFactory() {
        return llmProviderFactory;
    }

    /**
     * 清除适配器缓存（主要用于配置桥接后强制重建 provider）。
     */
    public void clearCache() {
        adapterCache.clear();
        log.info("[LargeLanguageModelFactory] adapter cache cleared");
    }
}
