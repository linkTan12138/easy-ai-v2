package com.link.easyai.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KimiConfig.class)
public class KimiAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "large-language-model.kimi.api", name = "key")
    public KimiConfig kimiConfig() {
        return new KimiConfig();
    }
}
