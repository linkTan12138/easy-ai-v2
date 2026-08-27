package com.link.easyai.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekConfig.class)
public class DeepSeekAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "large-language-model.deepseek.api", name = "key")
    public DeepSeekConfig deepSeekConfig() {
        return new DeepSeekConfig();
    }
}
