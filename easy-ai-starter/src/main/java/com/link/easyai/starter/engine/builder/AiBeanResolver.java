package com.link.easyai.starter.engine.builder;

/**
 * Resolves validator / action classes referenced by annotations into Spring
 * beans during config building.
 * <p>
 * Kept as a functional interface so the builder stays unit-testable without
 * a Spring context. The production implementation is backed by the
 * {@code ApplicationContext} and translates missing beans into
 * {@link com.link.easyai.starter.engine.exception.ConfigValidationException}
 * (fail-fast at startup).
 */
@FunctionalInterface
public interface AiBeanResolver {

    /**
     * Resolve the class into a bean instance.
     *
     * @param type the class referenced by the annotation
     * @return the bean instance (never null)
     * @throws com.link.easyai.starter.engine.exception.ConfigValidationException
     *         if the bean does not exist
     */
    <T> T resolve(Class<T> type);
}
