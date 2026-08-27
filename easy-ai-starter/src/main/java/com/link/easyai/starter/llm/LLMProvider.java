package com.link.easyai.starter.llm;

import java.util.List;
import java.util.stream.Stream;

/**
 * 大模型提供商统一接口（SPI）。
 * <p>
 * 内置实现（kimi / deepseek / doubao / openai_compatible）和客户自定义实现
 * 都必须实现此接口。客户可通过以下两种方式接入：
 * <ol>
 *   <li>配置中填写完整类名，由工厂反射加载</li>
 *   <li>通过 Java SPI（META-INF/services）注册后按 getName() 短名查找</li>
 * </ol>
 * 实现类约定：必须提供一个 {@code public XxxProvider(LLMConfig config)} 构造函数。
 */
public interface LLMProvider {

    /**
     * 同步对话。
     *
     * @param messages 对话消息列表（含 system / user / assistant）
     * @param config   模型配置（apiKey、endpoint、model、extra 等）
     * @return 模型返回的文本内容
     */
    String chat(List<Message> messages, LLMConfig config);

    /**
     * 流式对话（SSE）。
     *
     * @param messages 对话消息列表
     * @param config   模型配置
     * @return 文本片段流
     */
    Stream<String> streamChat(List<Message> messages, LLMConfig config);

    /**
     * 提供商唯一标识，用于 SPI 注册和配置中按名称查找。
     * 例如 "kimi"、"deepseek"、"doubao"、客户自定义 "my_company_llm"。
     */
    String getName();
}
