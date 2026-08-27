package com.link.easyai.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DoubaoConfig.class)
public class DoubaoAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "large-language-model.doubao.api", name = "key")
    public DoubaoConfig doubaoConfig() {
        return new DoubaoConfig();
    }
}
