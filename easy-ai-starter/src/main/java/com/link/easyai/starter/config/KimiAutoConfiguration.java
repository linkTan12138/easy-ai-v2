package com.link.easyai.starter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kimi 配置自动注册。
 * <p>
 * 通过 {@link EnableConfigurationProperties} 自动绑定 {@code large-language-model.kimi.api.*}
 * 配置到 {@link KimiConfig}，无需手动创建 Bean（手动 new 会导致属性未绑定）。
 */
@Configuration
@EnableConfigurationProperties(KimiConfig.class)
public class KimiAutoConfiguration {
}
