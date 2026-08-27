package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Classpath scanner that finds every class annotated with {@link AiTask}.
 * <p>
 * Unlike the default {@link ClassPathScanningCandidateComponentProvider} behaviour,
 * interfaces / abstract classes / enums carrying {@code @AiTask} are also returned
 * (deduplicated by class name) so that {@link AiTaskConfigBuilder} can reject them
 * with a precise error at startup instead of silently ignoring a broken declaration.
 */
@Component
public class AiAnnotationScanner {

    /**
     * Scan the given base packages for {@code @AiTask} classes.
     *
     * @param environment the Spring environment (used for @Profile-aware filtering)
     * @param classLoader the classloader to load scanned classes with
     * @param basePackages packages to scan; empty input yields an empty result
     * @return deduplicated classes annotated with @AiTask (never null)
     */
    public List<Class<?>> scan(Environment environment, ClassLoader classLoader, String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            return List.of();
        }

        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // Accept everything matched by the @AiTask include filter —
                        // structural problems (interface/abstract/enum) are reported
                        // by AiTaskConfigBuilder with actionable messages.
                        return true;
                    }
                };
        provider.addIncludeFilter(new AnnotationTypeFilter(AiTask.class));
        provider.setResourceLoader(new DefaultResourceLoader(classLoader));

        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            if (basePackage == null || basePackage.isBlank()) {
                continue;
            }
            Set<BeanDefinition> candidates = provider.findCandidateComponents(basePackage.trim());
            for (BeanDefinition candidate : candidates) {
                String className = candidate.getBeanClassName();
                try {
                    classes.add(ClassUtils.forName(className, classLoader));
                } catch (ClassNotFoundException e) {
                    throw new ConfigValidationException(
                            "无法加载 @AiTask 类: " + className, e);
                }
            }
        }
        return new ArrayList<>(classes);
    }
}
