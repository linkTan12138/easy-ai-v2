package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.exception.ConfigValidationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Production {@link AiBeanResolver} backed by the Spring {@code ApplicationContext}.
 * <p>
 * Validators and actions referenced from annotations <b>must</b> be Spring beans
 * (annotate them with {@code @AiValidator} / {@code @AiAction}, both of which are
 * meta-annotated with {@code @Component}) so their own dependencies (lookup
 * services, mappers, ...) are injected. A missing or ambiguous bean is reported
 * as a {@link ConfigValidationException} so the application fails fast at startup
 * with an actionable message instead of failing at task runtime.
 */
@Component
public class SpringAiBeanResolver implements AiBeanResolver {

    private final ApplicationContext applicationContext;

    public SpringAiBeanResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public <T> T resolve(Class<T> type) {
        try {
            return applicationContext.getBean(type);
        } catch (NoSuchBeanDefinitionException e) {
            // covers both "no bean" and "multiple beans" (NoUniqueBeanDefinitionException
            // extends NoSuchBeanDefinitionException)
            throw new ConfigValidationException(String.format(
                    "无法解析 %s 的 Spring Bean（不存在或存在多个候选）—— 请确认该类已用 @AiValidator/@AiAction（或 @Component）注册: %s",
                    type.getName(), e.getMessage()), e);
        }
    }
}
