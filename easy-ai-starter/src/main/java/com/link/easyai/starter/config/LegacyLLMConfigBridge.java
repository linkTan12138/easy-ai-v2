package com.link.easyai.starter.config;

import com.link.easyai.starter.llm.LLMConfig;
import com.link.easyai.starter.llm.LLMProviderFactory;
import com.link.easyai.starter.service.LargeLanguageModelFactory;
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
 * <b>重要：</b>桥接完成后必须清除 {@link LLMProviderFactory} 和
 * {@link LargeLanguageModelFactory} 的实例缓存。因为 {@link LargeLanguageModelHolder}
 * 在构造函数中就会触发 provider 创建，可能早于本桥接器的
 * {@link PostConstruct} 执行，导致 provider 用空配置创建并被缓存。
 *
 * @deprecated 仅用于平滑迁移，新应用请直接使用 {@code llm.*} 配置。
 */
@Component
@Deprecated
public class LegacyLLMConfigBridge {

    private static final Logger log = LoggerFactory.getLogger(LegacyLLMConfigBridge.class);

    private final LLMConfig llmConfig;
    private final LLMProviderFactory llmProviderFactory;
    private final LargeLanguageModelFactory largeLanguageModelFactory;

    @Autowired(required = false)
    private KimiConfig kimiConfig;

    @Autowired(required = false)
    private DeepSeekConfig deepSeekConfig;

    @Autowired(required = false)
    private DoubaoConfig doubaoConfig;

    public LegacyLLMConfigBridge(LLMConfig llmConfig,
                                  LLMProviderFactory llmProviderFactory,
                                  LargeLanguageModelFactory largeLanguageModelFactory) {
        this.llmConfig = llmConfig;
        this.llmProviderFactory = llmProviderFactory;
        this.largeLanguageModelFactory = largeLanguageModelFactory;
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
            // 关键：清除 provider 缓存，强制下次使用时用桥接后的新配置重建
            // （LargeLanguageModelHolder 可能在桥接之前就创建了用空配置的 provider）
            llmProviderFactory.clearCache();
            largeLanguageModelFactory.clearCache();
            log.info("[LegacyLLMConfigBridge] 已清除 LLM provider 缓存，将使用桥接后的配置重建");
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
            // 旧版 url 是完整接口地址（含 /chat/completions），新版 endpoint 期望 base URL
            // 自动移除 /chat/completions 后缀，避免 Provider 重复拼接导致 404
            String baseUrl = normalizeToBaseUrl(url);
            config.setEndpoint(baseUrl);
        }
        if (config.getModel() == null && model != null) {
            config.setModel(model);
        }
        providers.put(name, config);

        log.info("[LegacyLLMConfigBridge] 桥接 provider '{}': endpoint={} (原url={}), model={}, apiKey={}",
                name, config.getEndpoint(), url, model,
                key != null ? "***" + key.substring(Math.max(0, key.length() - 4)) : "null");
        return true;
    }

    /**
     * 将旧版完整 URL 规范化为 base URL。
     * 旧版配置如 https://api.deepseek.com/chat/completions → https://api.deepseek.com
     * 旧版配置如 https://api.moonshot.cn/v1/chat/completions → https://api.moonshot.cn/v1
     */
    private String normalizeToBaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String result = url.trim();
        // 移除末尾的 /chat/completions（大小写不敏感）
        if (result.toLowerCase().endsWith("/chat/completions")) {
            result = result.substring(0, result.length() - "/chat/completions".length());
        }
        // 移除末尾斜杠
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
