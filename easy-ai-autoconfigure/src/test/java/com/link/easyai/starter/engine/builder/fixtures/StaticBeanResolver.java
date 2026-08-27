package com.link.easyai.starter.engine.builder.fixtures;

import com.link.easyai.starter.engine.builder.AiBeanResolver;
import com.link.easyai.starter.engine.exception.ConfigValidationException;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only {@link AiBeanResolver} backed by an explicit instance map.
 * (AiBeanResolver's method declares its own type parameter, so it cannot be
 * implemented with a lambda — a named class is required.)
 */
public class StaticBeanResolver implements AiBeanResolver {

    private final Map<Class<?>, Object> beans = new HashMap<>();

    public StaticBeanResolver(Object... instances) {
        for (Object instance : instances) {
            beans.put(instance.getClass(), instance);
        }
    }

    @Override
    public <T> T resolve(Class<T> type) {
        Object bean = beans.get(type);
        if (bean == null) {
            throw new ConfigValidationException("no bean registered for " + type.getName());
        }
        return type.cast(bean);
    }
}
