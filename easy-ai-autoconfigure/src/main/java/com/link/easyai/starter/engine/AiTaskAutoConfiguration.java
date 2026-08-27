package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.action.ActionRegistrar;
import com.link.easyai.starter.engine.action.ActionRegistry;
import com.link.easyai.starter.engine.validation.ValidatorRegistrar;
import com.link.easyai.starter.engine.validation.ValidatorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

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
public class AiTaskAutoConfiguration {

    // Components in com.link.easyai.starter.engine are auto-scanned:
    // - DefaultAiTaskEngine, DefaultFieldSelector, DefaultPremiseEngine
    // - ValidatorRegistry, ValidatorRegistrar
    // - ActionRegistry, ActionRegistrar
    // - All @AiValidator, @AiAction, @AiPostAction annotated beans
}
