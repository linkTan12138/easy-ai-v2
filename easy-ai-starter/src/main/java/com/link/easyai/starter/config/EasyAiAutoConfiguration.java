package com.link.easyai.starter.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Easy-AI Starter 自动配置。
 * <p>
 * 扫描 service / controller / llm 包，注册 Mapper，
 * 并创建 {@link LLMlHolder} 持有当前激活的大模型。
 */
@Configuration
@ComponentScan({
        "com.link.easyai.starter.service",
        "com.link.easyai.starter.controller",
        "com.link.easyai.starter.llm",
        "com.link.easyai.starter.config"
})
@MapperScan("com.link.easyai.starter.mapper")
@ConditionalOnProperty(prefix = "easy-ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EasyAiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LLMlHolder llmHolder(
            com.link.easyai.starter.llm.LLMProviderFactory llmProviderFactory,
            @Value("${llm.provider:deepseek}") String active
    ) {
        return new LLMlHolder(llmProviderFactory, active);
    }
}
