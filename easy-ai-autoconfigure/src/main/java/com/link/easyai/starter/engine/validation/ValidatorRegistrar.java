package com.link.easyai.starter.engine.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Auto-registers all {@link FieldValidator} beans into {@link ValidatorRegistry}
 * on application startup.
 * <p>
 * Validators simply need to implement FieldValidator and be annotated with @Component
 * (or @AiValidator which is meta-annotated with @Component).
 */
@Component
public class ValidatorRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ValidatorRegistry validatorRegistry;

    @Autowired
    public ValidatorRegistrar(ValidatorRegistry validatorRegistry) {
        this.validatorRegistry = validatorRegistry;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        Map<String, FieldValidator> beans = ctx.getBeansOfType(FieldValidator.class);
        for (FieldValidator validator : beans.values()) {
            validatorRegistry.register(validator);
        }
    }
}
