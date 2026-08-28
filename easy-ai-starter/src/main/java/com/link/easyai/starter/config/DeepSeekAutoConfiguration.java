package com.link.easyai.starter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DeepSeek 配置自动注册。
 * <p>
 * 通过 {@link EnableConfigurationProperties} 自动绑定 {@code large-language-model.deepseek.api.*}
 * 配置到 {@link DeepSeekConfig}，无需手动创建 Bean（手动 new 会导致属性未绑定）。
 */
@Configuration
@EnableConfigurationProperties(DeepSeekConfig.class)
public class DeepSeekAutoConfiguration {
}
