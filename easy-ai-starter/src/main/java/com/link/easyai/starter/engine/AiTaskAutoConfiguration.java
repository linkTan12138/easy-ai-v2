package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.util.SnowflakeIdGenerator;
import com.link.easyai.starter.engine.validation.ValidatorRegistrar;
import com.link.easyai.starter.engine.validation.ValidatorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for the AI Task Engine.
 * <p>
 * Scans the engine package for components (validators, actions, engines)
 * and registers the auto-registrars for validators and actions.
 * <p>
 * This is separate from the existing {@link com.link.easyai.starter.config.EasyAiAutoConfiguration}
 * to keep the engine self-contained.
 */
@Configuration
@EnableConfigurationProperties(AiTaskProperties.class)
@ConditionalOnProperty(prefix = "easy-ai.task-engine", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan("com.link.easyai.starter.engine")
@EnableScheduling
public class AiTaskAutoConfiguration {

    // Components in com.link.easyai.starter.engine are auto-scanned:
    // - DefaultAiTaskEngine, DefaultFieldSelector, DefaultPremiseEngine
    // - ValidatorRegistry, ValidatorRegistrar
    // - ActionRegistry, ActionRegistrar
    // - All @AiValidator, @AiAction, @AiPostAction annotated beans

    /**
     * 雪花算法ID生成器。用于生成全局唯一、趋势递增的任务ID，
     * 替代原有的"时间戳+随机数"方案，消除高并发下的ID碰撞风险。
     */
    @Bean
    @ConditionalOnMissingBean
    public SnowflakeIdGenerator snowflakeIdGenerator(AiTaskProperties properties) {
        return new SnowflakeIdGenerator(
                properties.getSnowflake().getWorkerId(),
                properties.getSnowflake().getDatacenterId());
    }
}
