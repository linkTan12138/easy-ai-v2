package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.annotation.AiTaskParam;
import com.link.easyai.starter.engine.builder.AiAnnotationScanner;
import com.link.easyai.starter.engine.builder.AiBeanResolver;
import com.link.easyai.starter.engine.builder.AiTaskConfigBuilder;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import com.link.easyai.starter.engine.task.AiTask;
import com.link.easyai.starter.engine.task.TaskExecutor;
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
import java.util.stream.Collectors;

/**
 * 注解为主的 {@link AiTaskConfigService} 路由实现。
 * <p>
 * 任务结构与执行逻辑一律来自 {@code @AiTask} / {@code @AiTaskParam} 注解（代码即配置，
 * 启动时扫描构建并强校验）。数据库配置仅作为<b>字段提取规则覆盖</b>存在，
 * 合并优先级：<b>租户覆盖 &gt; 全局覆盖 &gt; 注解默认</b>。
 * <ul>
 *   <li>{@link #getLatestPublished}/{@link #get} 返回「注解配置 + 数据库覆盖合并」后的完整配置；</li>
 *   <li>覆盖按 {@code (taskType, fieldCode)} 精确匹配，引用不存在的字段在保存草稿时即报错；</li>
 *   <li>合并结果为拷贝，不污染共享的注解配置缓存；</li>
 *   <li>注解任务恒有 version=1 作为基准；存在数据库覆盖时版本号取覆盖版本。</li>
 * </ul>
 * 所有 {@code @AiTask} 类由 {@link AiAnnotationScanner} 扫描构建并校验一次
 * （{@link ContextRefreshedEvent}），结构性错误会直接导致启动失败。
 * <p>
 * 关闭注解模式：{@code easy-ai.task-engine.annotation.enabled=false}（已废弃，仅作兼容——
 * 任务必须来自注解，数据库不再承载完整任务配置）。
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "easy-ai.task-engine.annotation", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class AnnotationAiTaskConfigService implements AiTaskConfigService,
        ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(AnnotationAiTaskConfigService.class);

    /** 框架内置 @AiTask 执行器所在的包，始终扫描，不依赖用户的 base-packages 配置。 */
    private static final String BUILTIN_TASK_PACKAGE = "com.link.easyai.starter.engine.task.builtin";

    private final AiAnnotationScanner scanner;
    private final AiTaskConfigBuilder builder;
    private final AiBeanResolver beanResolver;
    private final AiTaskProperties properties;
    private final ObjectProvider<ExtractionOverrideStore> overrideStore;
    private final ApplicationContext applicationContext;

    /** taskType → built config. Populated once at startup, read-only afterwards. */
    private final Map<String, AiTaskConfig> annotationConfigs = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    public AnnotationAiTaskConfigService(AiAnnotationScanner scanner,
                                         AiTaskConfigBuilder builder,
                                         AiBeanResolver beanResolver,
                                         AiTaskProperties properties,
                                         ObjectProvider<ExtractionOverrideStore> overrideStore,
                                         ApplicationContext applicationContext) {
        this.scanner = scanner;
        this.builder = builder;
        this.beanResolver = beanResolver;
        this.properties = properties;
        this.overrideStore = overrideStore;
        this.applicationContext = applicationContext;
    }

    // ---- Startup: scan → build → validate → cache ----

    @Override
    public synchronized void onApplicationEvent(ContextRefreshedEvent event) {
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

    @SuppressWarnings("unchecked")
    private void buildAllConfigs() {
        if (initialized) {
            return;
        }

        String[] basePackages = resolveBasePackages();
        List<String> allPackages = new ArrayList<>(Arrays.asList(basePackages));
        if (!allPackages.contains(BUILTIN_TASK_PACKAGE)) {
            allPackages.add(BUILTIN_TASK_PACKAGE);
        }
        String[] packages = allPackages.toArray(new String[0]);

        // ---- 阶段一：扫描所有 @AiTask 执行器，构建基础配置 ----
        List<Class<?>> executorClasses = scanner.scan(
                applicationContext.getEnvironment(), applicationContext.getClassLoader(),
                AiTask.class, packages);

        List<String> errors = new ArrayList<>();
        Map<String, AiTaskConfig> built = new LinkedHashMap<>();
        Map<String, Class<?>> sources = new LinkedHashMap<>();

        for (Class<?> clazz : executorClasses) {
            if (!TaskExecutor.class.isAssignableFrom(clazz)) {
                errors.add("@AiTask 只能标注在 TaskExecutor 实现类上: " + clazz.getName());
                continue;
            }
            AiTaskConfig config;
            try {
                config = builder.buildBaseConfig((Class<? extends TaskExecutor>) clazz);
            } catch (ConfigValidationException e) {
                errors.add(e.getMessage());
                continue;
            }
            Class<?> previous = sources.put(config.getTaskType(), clazz);
            if (previous != null) {
                errors.add(String.format("taskType '%s' 重复声明: %s 与 %s",
                        config.getTaskType(), previous.getName(), clazz.getName()));
                continue;
            }
            built.put(config.getTaskType(), config);
        }

        // ---- 阶段二：扫描所有 @AiTaskParam DTO，通过 type 匹配补充字段 ----
        List<Class<?>> paramClasses = scanner.scan(
                applicationContext.getEnvironment(), applicationContext.getClassLoader(),
                AiTaskParam.class, packages);

        for (Class<?> paramClass : paramClasses) {
            AiTaskParam param = paramClass.getAnnotation(AiTaskParam.class);
            String type = param.type();
            AiTaskConfig config = built.get(type);
            if (config == null) {
                errors.add(String.format("@AiTaskParam '%s' 找不到对应的 @AiTask 执行器: %s",
                        type, paramClass.getName()));
                continue;
            }
            try {
                List<FieldDefinition> fields = builder.buildFields(paramClass, beanResolver);
                config.setFields(fields);
                CompletionConfig completion = builder.buildCompletion(fields);
                config.setCompletion(completion);
            } catch (ConfigValidationException e) {
                errors.add(e.getMessage());
            }
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
            log.info("[AiTaskConfig] 注解配置就绪: {} 个任务 {} (version={}, 数据库字段覆盖作为补充)",
                    built.size(), new TreeMap<>(built).keySet(),
                    AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION);
        }
        logExistingOverrides(built);
    }

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

    private void logExistingOverrides(Map<String, AiTaskConfig> built) {
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        if (store == null || built.isEmpty()) {
            return;
        }
        for (String taskType : built.keySet()) {
            try {
                Integer version = store.getLatestVersion(taskType, null);
                if (version != null) {
                    log.info("[AiTaskConfig] taskType '{}' 存在数据库字段提取覆盖 (version={})，将合并进注解配置", taskType, version);
                }
            } catch (Exception e) {
                log.debug("[AiTaskConfig] 无法检查数据库中是否存在同名覆盖: {}", e.getMessage());
            }
        }
    }

    // ---- Read routing: annotation base + database overrides merged ----

    /**
     * 获取所有注解配置的任务（用于功能介绍等场景，不含数据库覆盖）。
     */
    public Map<String, AiTaskConfig> getAllAnnotationConfigs() {
        ensureInitialized();
        return Collections.unmodifiableMap(annotationConfigs);
    }

    @Override
    public AiTaskConfig getLatestPublished(String taskType, String tenantId) {
        ensureInitialized();
        AiTaskConfig config = annotationConfigs.get(taskType);
        if (config == null) {
            throw new ConfigNotFoundException(taskType);
        }
        AiTaskConfig merged = copyConfig(config);
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        if (store != null) {
            FieldExtractionOverrides overrides = store.getPublishedOverrides(taskType, tenantId);
            mergeOverrides(merged, overrides);
            if (overrides != null && overrides.getVersion() != null) {
                merged.setVersion(overrides.getVersion());
            }
        }
        return merged;
    }

    @Override
    public AiTaskConfig get(String taskType, Integer version, String tenantId) {
        ensureInitialized();
        AiTaskConfig config = annotationConfigs.get(taskType);
        if (config == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        AiTaskConfig merged = copyConfig(config);
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        if (store != null) {
            FieldExtractionOverrides overrides = store.getOverrides(taskType, version, tenantId);
            mergeOverrides(merged, overrides);
        }
        merged.setVersion(version);
        return merged;
    }

    @Override
    public Integer getLatestVersion(String taskType, String tenantId) {
        ensureInitialized();
        if (!annotationConfigs.containsKey(taskType)) {
            return null;
        }
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        if (store == null) {
            return AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION;
        }
        Integer overrideVersion = store.getLatestVersion(taskType, tenantId);
        return overrideVersion != null ? overrideVersion : AiTaskConfigBuilder.ANNOTATION_CONFIG_VERSION;
    }

    // ---- Lifecycle management: pure extraction-override delegation ----

    @Override
    public AiTaskConfigRecord saveDraft(String taskType, String tenantId, FieldExtractionOverrides overrides) {
        ensureInitialized();
        // 校验：覆盖字段必须存在于注解字段中（防止静默失效）
        AiTaskConfig annotation = annotationConfigs.get(taskType);
        if (annotation == null) {
            throw new ConfigValidationException(String.format(
                    "taskType '%s' 未由 @AiTask 注解声明，任务配置只能来自注解", taskType));
        }
        validateOverrides(annotation, overrides);
        ExtractionOverrideStore store = requireStore(taskType);
        return store.saveDraft(taskType, tenantId, overrides);
    }

    @Override
    public AiTaskConfigRecord publish(String taskType, Integer version, String tenantId) {
        return requireStore(taskType).publish(taskType, version, tenantId);
    }

    @Override
    public AiTaskConfigRecord disable(String taskType, Integer version, String tenantId) {
        return requireStore(taskType).disable(taskType, version, tenantId);
    }

    @Override
    public List<AiTaskConfigRecord> list(String taskType, String tenantId) {
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        return store == null ? List.of() : store.list(taskType, tenantId);
    }

    // ---- Override merging ----

    /**
     * 校验覆盖集：每个 fieldCode 必须存在于注解字段中。
     */
    private void validateOverrides(AiTaskConfig annotation, FieldExtractionOverrides overrides) {
        if (overrides == null || overrides.getFields() == null || overrides.getFields().isEmpty()) {
            return;
        }
        List<String> known = annotation.getFields().stream()
                .map(FieldDefinition::getCode).collect(Collectors.toList());
        for (String fieldCode : overrides.getFields().keySet()) {
            if (fieldCode == null || !known.contains(fieldCode)) {
                throw new ConfigValidationException(String.format(
                        "taskType='%s' 的字段提取覆盖引用了不存在的字段 code='%s'（注解字段: %s）",
                        annotation.getTaskType(), fieldCode, known));
            }
        }
    }

    /**
     * 将数据库覆盖合并进注解配置的拷贝。覆盖项仅在非 null/非空时生效。
     * 合并过程中再次校验字段存在性（防御历史遗留脏数据）。
     */
    private void mergeOverrides(AiTaskConfig merged, FieldExtractionOverrides overrides) {
        if (overrides == null || overrides.getFields() == null || overrides.getFields().isEmpty()) {
            return;
        }
        for (Map.Entry<String, ExtractionConfig> entry : overrides.getFields().entrySet()) {
            FieldDefinition field = merged.getField(entry.getKey());
            if (field == null) {
                throw new ConfigValidationException(String.format(
                        "taskType='%s' 的字段提取覆盖引用了不存在的字段 code='%s'（注解字段: %s）",
                        merged.getTaskType(), entry.getKey(),
                        merged.getFields().stream().map(FieldDefinition::getCode).collect(Collectors.toList())));
            }
            ExtractionConfig override = entry.getValue();
            if (override == null) {
                continue;
            }
            ExtractionConfig base = field.getExtraction() != null
                    ? field.getExtraction()
                    : new ExtractionConfig();
            if (override.getDescription() != null && !override.getDescription().isBlank()) {
                base.setDescription(override.getDescription());
            }
            if (override.getExamples() != null && !override.getExamples().isEmpty()) {
                base.setExamples(new ArrayList<>(override.getExamples()));
            }
            if (override.getRules() != null && !override.getRules().isEmpty()) {
                base.setRules(new ArrayList<>(override.getRules()));
            }
            if (override.isAllowEmpty()) {
                base.setAllowEmpty(true);
            }
            field.setExtraction(base);
        }
    }

    /**
     * 深拷贝注解配置，避免合并污染共享缓存。extraction 等不可变字段先共享，
     * 仅在命中覆盖时替换为拷贝后的对象。
     */
    private AiTaskConfig copyConfig(AiTaskConfig source) {
        AiTaskConfig copy = AiTaskConfig.builder()
                .taskType(source.getTaskType())
                .version(source.getVersion())
                .name(source.getName())
                .description(source.getDescription())
                .keywords(source.getKeywords() != null ? new ArrayList<>(source.getKeywords()) : null)
                .examples(source.getExamples() != null ? new ArrayList<>(source.getExamples()) : null)
                .completion(source.getCompletion())
                .executeConfig(source.getExecuteConfig())
                .extensions(source.getExtensions() != null ? new LinkedHashMap<>(source.getExtensions()) : null)
                .build();
        List<FieldDefinition> fields = new ArrayList<>();
        if (source.getFields() != null) {
            for (FieldDefinition field : source.getFields()) {
                fields.add(FieldDefinition.builder()
                        .code(field.getCode())
                        .name(field.getName())
                        .type(field.getType())
                        .required(field.isRequired())
                        .extraction(field.getExtraction())
                        .premise(field.getPremise())
                        .validation(field.getValidation())
                        .normalization(field.getNormalization())
                        .mappings(field.getMappings() != null ? new ArrayList<>(field.getMappings()) : null)
                        .options(field.getOptions() != null ? new ArrayList<>(field.getOptions()) : null)
                        .order(field.getOrder())
                        .sensitive(field.isSensitive())
                        .build());
            }
        }
        copy.setFields(fields);
        return copy;
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

    private ExtractionOverrideStore requireStore(String taskType) {
        ExtractionOverrideStore store = overrideStore.getIfAvailable();
        if (store == null) {
            throw new ConfigNotFoundException(taskType);
        }
        return store;
    }
}
