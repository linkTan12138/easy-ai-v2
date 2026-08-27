package com.link.easyai.starter.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Easy-AI Starter 自动配置。
 * <p>
 * 扫描 service / controller / llm 包，注册 Mapper，启用 LLM 相关配置属性，
 * 并创建 {@link LargeLanguageModelHolder} 持有当前激活的大模型。
 */
@Configuration
@ComponentScan({
        "com.link.easyai.starter.service",
        "com.link.easyai.starter.controller",
        "com.link.easyai.starter.llm",
        "com.link.easyai.starter.config"
})
@MapperScan("com.link.easyai.starter.mapper")
@EnableConfigurationProperties({KimiConfig.class, DoubaoConfig.class, DeepSeekConfig.class})
@ConditionalOnProperty(prefix = "easy-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EasyAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LargeLanguageModelHolder largeLanguageModelHolder(
            com.link.easyai.starter.service.LargeLanguageModelFactory largeLanguageModelFactory,
            @Value("${large-language-model.active:${llm.provider:kimi}}") String active
    ) {
        return new LargeLanguageModelHolder(largeLanguageModelFactory, active);
    }
}
