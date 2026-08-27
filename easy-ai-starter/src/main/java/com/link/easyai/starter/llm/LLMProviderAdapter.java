package com.link.easyai.starter.llm;

import cn.hutool.json.JSONUtil;
import com.link.easyai.starter.service.LargeLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 适配器：将新的 {@link LLMProvider} SPI 桥接到旧的 {@link LargeLanguageModel} 业务接口。
 * <p>
 * 使上层代码（如 {@code LlmClient}、{@code LargeLanguageModelFactory}、{@code LargeLanguageModelHolder}）
 * 无需任何改动即可使用插件化的新 Provider 体系。
 * <p>
 * 方法映射：
 * <ul>
 *   <li>{@code chatCompletion(system, msg)} → 组装 system + user 两条 Message 调用 {@link LLMProvider#chat}</li>
 *   <li>{@code chatCompletion(msg)} → 单条 user Message 调用</li>
 *   <li>{@code chatCompletion(system, msg, clazz)} → 调用后将 JSON 结果反序列化为目标类型</li>
 * </ul>
 */
public class LLMProviderAdapter implements LargeLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(LLMProviderAdapter.class);

    private final LLMProvider provider;
    private final LLMConfig llmConfig;

    public LLMProviderAdapter(LLMProvider provider, LLMConfig llmConfig) {
        this.provider = provider;
        this.llmConfig = llmConfig;
    }

    @Override
    public String chatCompletion(String msg) {
        List<Message> messages = new ArrayList<>(1);
        messages.add(new Message("user", msg));
        return provider.chat(messages, llmConfig);
    }

    @Override
    public String chatCompletion(String system, String msg) {
        List<Message> messages = new ArrayList<>(2);
        if (system != null && !system.isBlank()) {
            messages.add(new Message("system", system));
        }
        messages.add(new Message("user", msg));
        return provider.chat(messages, llmConfig);
    }

    @Override
    public <T> T chatCompletion(String system, String msg, Class<T> clazz) {
        try {
            String res = chatCompletion(system, msg);
            return JSONUtil.parseObj(res).toBean(clazz);
        } catch (Exception e) {
            log.warn("[LLMProviderAdapter] JSON parse to {} failed: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 获取底层 Provider 名称。
     */
    public String getProviderName() {
        return provider.getName();
    }

    /**
     * 获取底层 Provider 实例。
     */
    public LLMProvider getProvider() {
        return provider;
    }
}
