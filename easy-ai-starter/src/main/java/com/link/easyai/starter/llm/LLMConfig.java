package com.link.easyai.starter.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置属性，对应 application.yml 中的 {@code llm.*} 配置。
 * <p>
 * 支持两种配置方式：
 * <ol>
 *   <li><b>单模型直配</b>：直接配置 provider / apiKey / endpoint / model，
 *       适用于只使用一个模型的简单场景。</li>
 *   <li><b>多模型配置</b>：通过 {@code providers} Map 为每个 provider 独立配置，
 *       适用于 fallback 多模型降级场景。key 为 provider 短名或完整类名。</li>
 * </ol>
 * 当 {@code providers} 中存在对应 name 的配置时优先使用；否则回退到顶层配置。
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class LLMConfig {

    /**
     * 默认激活的提供商：内置短名（kimi / deepseek / doubao / openai_compatible）
     * 或客户自定义实现类的完整类名。
     */
    private String provider = "deepseek";

    /** 顶层默认 API Key（当 providers 中未配置时使用） */
    private String apiKey;

    /** 顶层默认 endpoint（当 providers 中未配置时使用） */
    private String endpoint;

    /** 顶层默认 model（当 providers 中未配置时使用） */
    private String model;

    /** 顶层默认扩展参数（当 providers 中未配置时使用） */
    private Map<String, Object> extra = new HashMap<>();

    /**
     * 多 provider 独立配置。key 为 provider 短名（如 "kimi"）或完整类名。
     * 用于 fallback 链中不同模型使用不同密钥/端点的场景。
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    // ---- 顶层配置 getters / setters ----

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Map<String, ProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderConfig> providers) {
        this.providers = providers;
    }

    /**
     * 根据 provider name 获取有效配置。
     * 优先从 {@link #providers} 中查找，未找到则使用顶层默认配置。
     *
     * @param providerName provider 短名或完整类名
     * @return 该 provider 的有效配置（始终返回非 null 的新实例）
     */
    public ProviderConfig resolve(String providerName) {
        ProviderConfig specific = providers != null ? providers.get(providerName) : null;
        if (specific != null) {
            // 补全顶层默认值（specific 中未设置的字段回退到顶层）
            ProviderConfig merged = new ProviderConfig();
            merged.setApiKey(specific.getApiKey() != null ? specific.getApiKey() : this.apiKey);
            merged.setEndpoint(specific.getEndpoint() != null ? specific.getEndpoint() : this.endpoint);
            merged.setModel(specific.getModel() != null ? specific.getModel() : this.model);
            Map<String, Object> mergedExtra = new HashMap<>();
            if (this.extra != null) {
                mergedExtra.putAll(this.extra);
            }
            if (specific.getExtra() != null) {
                mergedExtra.putAll(specific.getExtra());
            }
            merged.setExtra(mergedExtra);
            return merged;
        }
        // 回退到顶层配置
        ProviderConfig fallback = new ProviderConfig();
        fallback.setApiKey(this.apiKey);
        fallback.setEndpoint(this.endpoint);
        fallback.setModel(this.model);
        fallback.setExtra(new HashMap<>(this.extra != null ? this.extra : new HashMap<>()));
        return fallback;
    }

    /**
     * 单个 provider 的配置。
     */
    public static class ProviderConfig {

        private String apiKey;
        private String endpoint;
        private String model;
        private Map<String, Object> extra = new HashMap<>();

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra;
        }
    }
}
