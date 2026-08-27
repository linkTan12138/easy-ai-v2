package com.link.easyai.starter.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM Provider 工厂（核心）。
 * <p>
 * 支持三种方式获取 Provider：
 * <ol>
 *   <li><b>内置短名</b>：kimi / deepseek / doubao / openai_compatible</li>
 *   <li><b>SPI 自动发现</b>：客户打 jar 引入后通过 META-INF/services 自动注册，按 getName() 查找</li>
 *   <li><b>完整类名反射</b>：配置中填写客户自定义实现类的完整类名</li>
 * </ol>
 * 优先级：内置短名 &gt; SPI 名称 &gt; 完整类名反射。
 * <p>
 * 实现类约定：必须提供一个 {@code public XxxProvider(LLMConfig.ProviderConfig config)} 构造函数。
 */
@Component
public class LLMProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(LLMProviderFactory.class);

    /** 内置短名 -> 实现类映射 */
    private static final Map<String, Class<? extends LLMProvider>> BUILTIN = new LinkedHashMap<>();

    /** SPI 发现的 name -> 实现类映射 */
    private static final Map<String, Class<? extends LLMProvider>> SPI_PROVIDERS = new LinkedHashMap<>();

    /** 实例缓存：providerName -> LLMProvider 实例（线程安全） */
    private final Map<String, LLMProvider> instanceCache = new ConcurrentHashMap<>();

    static {
        // 注册内置实现
        BUILTIN.put("kimi", KimiProvider.class);
        BUILTIN.put("deepseek", DeepSeekProvider.class);
        BUILTIN.put("doubao", DoubaoProvider.class);
        BUILTIN.put("openai_compatible", OpenAICompatibleProvider.class);

        // SPI 自动发现：扫描 classpath 中所有 META-INF/services/com.link.easyai.starter.llm.LLMProvider
        try {
            ServiceLoader<LLMProvider> loader = ServiceLoader.load(LLMProvider.class);
            for (LLMProvider provider : loader) {
                String name = provider.getName();
                SPI_PROVIDERS.put(name, provider.getClass());
                log.info("[LLMProviderFactory] SPI discovered provider: '{}' -> {}", name, provider.getClass().getName());
            }
        } catch (Exception e) {
            log.warn("[LLMProviderFactory] SPI scan failed: {}", e.getMessage());
        }
    }

    private final LLMConfig llmConfig;

    public LLMProviderFactory(LLMConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    /**
     * 根据 provider 名称创建（或获取缓存的）LLMProvider 实例。
     * 配置从全局 {@link LLMConfig} 中按 name 解析。
     *
     * @param providerName 内置短名 / SPI 名称 / 完整类名
     * @return LLMProvider 实例
     * @throws IllegalArgumentException 如果 provider 名称无法解析
     */
    public LLMProvider create(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("llm.provider 配置不能为空");
        }
        return instanceCache.computeIfAbsent(providerName, name -> {
            Class<? extends LLMProvider> clazz = resolveClass(name);
            LLMConfig.ProviderConfig config = llmConfig.resolve(name);
            return instantiate(clazz, config);
        });
    }

    /**
     * 使用显式提供的配置创建 LLMProvider 实例。
     *
     * @param providerName 内置短名 / SPI 名称 / 完整类名
     * @param config       该 provider 的配置
     * @return LLMProvider 实例
     */
    public LLMProvider create(String providerName, LLMConfig.ProviderConfig config) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName 不能为空");
        }
        Class<? extends LLMProvider> clazz = resolveClass(providerName);
        return instantiate(clazz, config);
    }

    /**
     * 使用默认 provider（由 llm.provider 配置指定）创建实例。
     */
    public LLMProvider createDefault() {
        return create(llmConfig.getProvider());
    }

    /**
     * 解析 provider 名称对应的实现类。
     * 优先级：内置短名 > SPI 名称 > 完整类名反射。
     */
    private Class<? extends LLMProvider> resolveClass(String providerName) {
        // 1. 内置短名
        if (BUILTIN.containsKey(providerName)) {
            return BUILTIN.get(providerName);
        }

        // 2. SPI 注册的名称
        if (SPI_PROVIDERS.containsKey(providerName)) {
            return SPI_PROVIDERS.get(providerName);
        }

        // 3. 当作完整类名反射加载
        try {
            Class<?> raw = Class.forName(providerName);
            if (!LLMProvider.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException(
                        "类 " + providerName + " 未实现 LLMProvider 接口");
            }
            return raw.asSubclass(LLMProvider.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "找不到 LLM 实现类: '" + providerName + "'。" +
                            "请检查配置。内置可选: " + BUILTIN.keySet() +
                            "，SPI 已注册: " + SPI_PROVIDERS.keySet() +
                            "，或填写自定义实现类的完整类名。", e);
        }
    }

    /**
     * 反射实例化 Provider。约定构造函数接收 {@link LLMConfig.ProviderConfig}。
     */
    private LLMProvider instantiate(Class<? extends LLMProvider> clazz, LLMConfig.ProviderConfig config) {
        try {
            Constructor<? extends LLMProvider> ctor = clazz.getConstructor(LLMConfig.ProviderConfig.class);
            return ctor.newInstance(config);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "LLM 实现类 " + clazz.getName() + " 必须提供一个接收 LLMConfig.ProviderConfig 的构造函数", e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("实例化 LLMProvider 失败: " + clazz.getName(), e);
        }
    }

    /**
     * 获取所有已注册的 provider 名称（用于排查问题）。
     */
    public Map<String, Object> getRegisteredProviders() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("builtin", BUILTIN.keySet());
        result.put("spi", SPI_PROVIDERS.keySet());
        result.put("default", llmConfig.getProvider());
        return result;
    }

    /**
     * 清除实例缓存（主要用于测试或配置热更新场景）。
     */
    public void clearCache() {
        instanceCache.clear();
    }
}
