package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.exception.ConfigValidationException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 类路径扫描器，查找标注了指定注解的所有类。
 * <p>
 * 与默认行为不同，接口/抽象类/枚举也会被返回（按类名去重），
 * 以便 {@link AiTaskConfigBuilder} 在启动时给出精确的错误信息，
 * 而不是静默忽略错误声明。
 */
@Component
public class AiAnnotationScanner {

    /**
     * 扫描指定包，查找标注了 {@code annotationType} 的类。
     *
     * @param environment    Spring 环境（用于 @Profile 过滤）
     * @param classLoader    加载扫描到的类的类加载器
     * @param annotationType 要扫描的注解类型
     * @param basePackages   要扫描的包；空输入返回空结果
     * @return 去重后的标注类列表（永不为 null）
     */
    public List<Class<?>> scan(Environment environment,
                               ClassLoader classLoader,
                               Class<? extends Annotation> annotationType,
                               String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            return List.of();
        }

        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false, environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // 接受所有被注解过滤器匹配的类 — 结构问题（接口/抽象/枚举）
                        // 由 AiTaskConfigBuilder 给出可操作的错误信息。
                        return true;
                    }
                };
        provider.addIncludeFilter(new AnnotationTypeFilter(annotationType));
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
                            "无法加载 @" + annotationType.getSimpleName() + " 类: " + className, e);
                }
            }
        }
        return new ArrayList<>(classes);
    }
}
