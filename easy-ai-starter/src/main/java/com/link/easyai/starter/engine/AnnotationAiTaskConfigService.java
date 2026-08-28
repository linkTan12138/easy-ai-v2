package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.builder.AiAnnotationScanner;
import com.link.easyai.starter.engine.builder.AiBeanResolver;
import com.link.easyai.starter.engine.builder.AiTaskConfigBuilder;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code @Primary} routing {@link AiTaskConfigService} that puts annotation-defined
 * task configs in front of the database-backed {@link DefaultAiTaskConfigService}.
 * <p>
 * Routing rules (one config source per task type — never merged):
 * <ul>
 *   <li>taskType declared via {@code @AiTask} → always served from code, version 1
 *       (annotation configs are immutable, hence version is fixed).</li>
 *   <li>anything else → delegated to the database service (if present).</li>
 *   <li>{@code get(taskType, version)} with version &gt; 1 → database: an in-flight
 *       task bound to an old DB version keeps resuming against that version even
 *       after the task type migrated to annotations.</li>
 * </ul>
 * All {@code @AiTask} classes found by {@link AiAnnotationScanner} are built and
 * validated once on {@link ContextRefreshedEvent} (after every validator/action
 * bean is ready). Structural problems — duplicate taskType, unknown validator
 * beans, dangling {@code @AiDependsOn} references, ... — fail the startup with the
 * complete list of errors. A database config shadowed by an annotation config is
 * logged as a warning, not an error (that is the intended migration path).
 * <p>
 * Disable with {@code easy-ai.task-engine.annotation.enabled=false} to fall back
 * to pure database-driven configuration.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "easy-ai.task-engine.annotation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AnnotationAiTaskConfigService implements AiTaskConfigService,
        ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(AnnotationAiTaskConfigService.class);

    /** 框架内置 @AiTask 配置所在的包，始终扫描，不依赖用户的 base-packages 配置。 */
    private static final String BUILTIN_CONFIG_PACKAGE = "com.link.easyai.starter.engine.config.builtin";

    private final AiAnnotationScanner scanner;
    private final AiTaskConfigBuilder builder;
    private final AiBeanResolver beanResolver;
    private final AiTaskProperties properties;
    private final ObjectProvider<DefaultAiTaskConfigService> databaseService;
    private final ApplicationContext applicationContext;

    /** taskType → built config. Populated once at startup, read-only afterwards. */
    private final Map<String, AiTaskConfig> annotationConfigs = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public AnnotationAiTaskConfigService(AiAnnotationScanner scanner,
                                         AiTaskConfigBuilder builder,
                                         AiBeanResolver beanResolver,
                                         AiTaskProperties properties,
                                         ObjectProvider<DefaultAiTaskConfigService> databaseService,
                                         ApplicationContext applicationContext) {
        this.scanner = scanner;
        this.builder = builder;
        this.beanResolver = beanResolver;
        this.properties = properties;
        this.databaseService = databaseService;
        this.applicationContext = applicationContext;
    }

    // ---- Startup: scan → build → validate → cache ----

    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
        // Only react to the refresh of the context this bean lives in —
        // child/parent context refreshes must not trigger a rebuild.
        if (event.getApplicationContext() != this.applicationContext) {
            return;
        }
        buildAllConfigs();
    }

    /** Lazy fallback so direct calls before the refresh event still work (tests). */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            buildAllConfigs();
        }
    }

    /** Caller must hold the monitor. All-or-nothing: no partial caches on error. */
    private void buildAllConfigs() {
        if (initialized) {
            return;
        }

        String[] basePackages = resolveBasePackages();
        // 合并用户配置的扫描包与框架内置包（内置功能场景始终可用，不依赖用户配置）
        List<String> allPackages = new ArrayList<>(Arrays.asList(basePackages));
        if (!allPackages.contains(BUILTIN_CONFIG_PACKAGE)) {
            allPackages.add(BUILTIN_CONFIG_PACKAGE);
        }
        List<Class<?>> taskClasses = scanner.scan(
                applicationContext.getEnvironment(), applicationContext.getClassLoader(),
                allPackages.toArray(new String[0]));

        List<String> errors = new ArrayList<>();
        Map<String, AiTaskConfig> built = new LinkedHashMap<>();
        Map<String, Class<?>> sources = new LinkedHashMap<>();

        for (Class<?> taskClass : taskClasses) {
            AiTaskConfig config;
            try {
                config = builder.build(taskClass, beanResolver);
            } catch (ConfigValidationException e) {
                errors.add(e.getMessage());
                continue;
            }
            Class<?> previous = sources.put(config.getTaskType(), taskClass);
            if (previous != null) {
                errors.add(String.format("taskType '%s' 重复声明: %s 与 %s",
                        config.getTaskType(), previous.getName(), taskClass.getName()));
                continue;
            }
            built.put(config.getTaskType(), config);
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException("注解任务配置构建失败:\n- "
                    + String.join("\n- ", errors));
        }

        annotationConfigs.putAll(built);
        initialized = true;

        if (built.isEmpty()) {
            log.info("[AiTaskConfig] 未发现 @AiTask 注解配置 (扫描包: {})",
                    String.join(", ", basePackages));
        } else {
            log.info("[AiTaskConfig] 注解配置就绪: {} 个任务 {} (version={}, 数据库配置作为兜底)",
                    built.size(), new TreeMap<>(built).keySet(),
                    AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION);
        }
        warnShadowedDatabaseConfigs(built);
    }

    /**
     * Base packages: explicit configuration wins; otherwise default to the
     * Spring Boot application package. Non-Boot contexts yield an empty array
     * (nothing scanned — pure database mode).
     */
    private String[] resolveBasePackages() {
        String[] configured = properties.getAnnotation().getBasePackages();
        if (configured != null && configured.length > 0) {
            return configured;
        }
        try {
            List<String> auto = AutoConfigurationPackages.get(applicationContext);
            return auto.toArray(new String[0]);
        } catch (IllegalStateException e) {
            return new String[0];
        }
    }

    private void warnShadowedDatabaseConfigs(Map<String, AiTaskConfig> built) {
        DefaultAiTaskConfigService database = databaseService.getIfAvailable();
        if (database == null || built.isEmpty()) {
            return;
        }
        for (String taskType : built.keySet()) {
            try {
                if (database.getLatestVersion(taskType) != null) {
                    log.warn("[AiTaskConfig] taskType '{}' 同时存在于注解与数据库中，"
                            + "注解配置优先生效，数据库配置将被忽略", taskType);
                }
            } catch (Exception e) {
                // Database not reachable during startup — skip the shadow check.
                log.debug("[AiTaskConfig] 无法检查数据库中是否存在同名配置: {}", e.getMessage());
            }
        }
    }

    // ---- Read routing: annotation first, database fallback ----

    /**
     * 获取所有注解配置的任务（用于功能介绍等场景）。
     *
     * @return 不可变的任务配置映射（taskType → config）
     */
    public Map<String, AiTaskConfig> getAllAnnotationConfigs() {
        ensureInitialized();
        return Collections.unmodifiableMap(annotationConfigs);
    }

    @Override
    public AiTaskConfig getLatestPublished(String taskType) {
        ensureInitialized();
        AiTaskConfig config = annotationConfigs.get(taskType);
        if (config != null) {
            return config;
        }
        return requireDatabase(taskType).getLatestPublished(taskType);
    }

    @Override
    public AiTaskConfig get(String taskType, Integer version) {
        ensureInitialized();
        AiTaskConfig config = annotationConfigs.get(taskType);
        // Annotation configs are always version 1. A request for another version
        // is an in-flight task bound to a legacy database version — delegate it.
        if (config != null && Integer.valueOf(AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION).equals(version)) {
            return config;
        }
        return requireDatabase(taskType).get(taskType, version);
    }

    @Override
    public Integer getLatestVersion(String taskType) {
        ensureInitialized();
        if (annotationConfigs.containsKey(taskType)) {
            return AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION;
        }
        DefaultAiTaskConfigService database = databaseService.getIfAvailable();
        // Contract: null when no version exists — not an exception.
        return database == null ? null : database.getLatestVersion(taskType);
    }

    // ---- Lifecycle management: pure database delegation ----

    @Override
    public AiTaskConfigRecord saveDraft(AiTaskConfig config) {
        String taskType = config == null ? null : config.getTaskType();
        if (annotationConfigs.containsKey(taskType)) {
            throw new ConfigValidationException(String.format(
                    "taskType '%s' 由 @AiTask 注解定义（代码即配置，不可在数据库另存草稿）", taskType));
        }
        return requireDatabase(taskType).saveDraft(config);
    }

    @Override
    public AiTaskConfigRecord publish(String taskType, Integer version) {
        return requireDatabase(taskType).publish(taskType, version);
    }

    @Override
    public AiTaskConfigRecord disable(String taskType, Integer version) {
        return requireDatabase(taskType).disable(taskType, version);
    }

    @Override
    public List<AiTaskConfigRecord> list(String taskType) {
        DefaultAiTaskConfigService database = databaseService.getIfAvailable();
        return database == null ? List.of() : database.list(taskType);
    }

    // ---- Diagnostics (tests / ops) ----

    /** @return unmodifiable view of all annotation-built configs (taskType → config) */
    public Map<String, AiTaskConfig> getAnnotationConfigs() {
        ensureInitialized();
        return Collections.unmodifiableMap(annotationConfigs);
    }

    /** @return whether the taskType is declared via @AiTask and served from code */
    public boolean isAnnotationConfigured(String taskType) {
        ensureInitialized();
        return annotationConfigs.containsKey(taskType);
    }

    private DefaultAiTaskConfigService requireDatabase(String taskType) {
        DefaultAiTaskConfigService database = databaseService.getIfAvailable();
        if (database == null) {
            // No database-backed service in the context: for read paths this is
            // simply "config not found"; for lifecycle paths it is a setup error.
            throw new ConfigNotFoundException(taskType);
        }
        return database;
    }
}
