package com.link.easyai.starter.config;

import com.link.easyai.starter.llm.LLMConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 旧配置桥接器（向后兼容）。
 * <p>
 * 将旧版 {@code large-language-model.{provider}.api.*} 配置自动迁移到
 * 新版 {@code llm.providers.{provider}.*} 配置中，确保使用旧配置的应用
 * 无需修改即可正常运行。
 * <p>
 * 迁移映射：
 * <ul>
 *   <li>{@code large-language-model.kimi.api.key} → {@code llm.providers.kimi.api-key}</li>
 *   <li>{@code large-language-model.kimi.api.url} → {@code llm.providers.kimi.endpoint}</li>
 *   <li>{@code large-language-model.kimi.api.model} → {@code llm.providers.kimi.model}</li>
 *   <li>deepseek / doubao 同理</li>
 *   <li>{@code large-language-model.active} → {@code llm.provider}（仅当 llm.provider 未设置时）</li>
 * </ul>
 *
 * @deprecated 仅用于平滑迁移，新应用请直接使用 {@code llm.*} 配置。
 */
@Component
@Deprecated
public class LegacyLLMConfigBridge {

    private static final Logger log = LoggerFactory.getLogger(LegacyLLMConfigBridge.class);

    private final LLMConfig llmConfig;

    @Autowired(required = false)
    private KimiConfig kimiConfig;

    @Autowired(required = false)
    private DeepSeekConfig deepSeekConfig;

    @Autowired(required = false)
    private DoubaoConfig doubaoConfig;

    public LegacyLLMConfigBridge(LLMConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    @PostConstruct
    public void bridge() {
        boolean bridged = false;

        if (kimiConfig != null && kimiConfig.getKey() != null) {
            bridged |= mergeProvider("kimi", kimiConfig.getKey(), kimiConfig.getUrl(), kimiConfig.getModel());
        }
        if (deepSeekConfig != null && deepSeekConfig.getKey() != null) {
            bridged |= mergeProvider("deepseek", deepSeekConfig.getKey(), deepSeekConfig.getUrl(), deepSeekConfig.getModel());
        }
        if (doubaoConfig != null && doubaoConfig.getKey() != null) {
            bridged |= mergeProvider("doubao", doubaoConfig.getKey(), doubaoConfig.getUrl(), doubaoConfig.getModel());
        }

        if (bridged) {
            log.info("[LegacyLLMConfigBridge] 已将旧版 large-language-model.* 配置桥接到 llm.providers.*");
        }
    }

    /**
     * 将旧配置合并到 LLMConfig.providers 中。
     * 如果新配置中已存在该 provider 的配置，则不覆盖（新配置优先）。
     *
     * @return 是否实际执行了桥接
     */
    private boolean mergeProvider(String name, String key, String url, String model) {
        Map<String, LLMConfig.ProviderConfig> providers = llmConfig.getProviders();
        if (providers == null) {
            providers = new HashMap<>();
            llmConfig.setProviders(providers);
        }

        // 新配置优先：如果已存在且 apiKey 已设置，则不覆盖
        LLMConfig.ProviderConfig existing = providers.get(name);
        if (existing != null && existing.getApiKey() != null) {
            return false;
        }

        LLMConfig.ProviderConfig config = existing != null ? existing : new LLMConfig.ProviderConfig();
        if (config.getApiKey() == null) {
            config.setApiKey(key);
        }
        if (config.getEndpoint() == null && url != null) {
            config.setEndpoint(url);
        }
        if (config.getModel() == null && model != null) {
            config.setModel(model);
        }
        providers.put(name, config);

        log.info("[LegacyLLMConfigBridge] 桥接 provider '{}': endpoint={}, model={}", name, url, model);
        return true;
    }
}
