package com.link.easyai.starter.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"com.link.easyai.starter.service", "com.link.easyai.starter.controller"})
@MapperScan("com.link.easyai.starter.mapper")
@EnableConfigurationProperties({KimiConfig.class, DoubaoConfig.class, DeepSeekConfig.class})
@ConditionalOnProperty(prefix = "easy-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EasyAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AiSceneHolder aiSceneHolder() {
        return new AiSceneHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public LargeLanguageModelHolder largeLanguageModelHolder(
            com.link.easyai.starter.service.LargeLanguageModelFactory largeLanguageModelFactory,
            @Value("${large-language-model.active:kimi}") String active
    ) {
        return new LargeLanguageModelHolder(largeLanguageModelFactory, active);
    }
}
