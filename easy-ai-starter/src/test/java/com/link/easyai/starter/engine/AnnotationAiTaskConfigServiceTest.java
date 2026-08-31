package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.builder.AiAnnotationScanner;
import com.link.easyai.starter.engine.builder.AiTaskConfigBuilder;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;
import com.link.easyai.starter.engine.builder.fixtures.StaticBeanResolver;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import com.link.easyai.starter.engine.validation.builtin.EnumValidator;
import com.link.easyai.starter.engine.validation.builtin.NotEmptyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link AnnotationAiTaskConfigService}: annotation-first routing with
 * DB extraction overrides merged (tenant-first, global fallback, annotation default),
 * field-code existence validation, in-flight version routing, and startup validation.
 */
@SuppressWarnings("unchecked")
class AnnotationAiTaskConfigServiceTest {

    private static final String VALID_PKG = "com.link.easyai.starter.engine.builder.fixtures.valid";
    private static final String DUPLICATE_PKG = "com.link.easyai.starter.engine.builder.fixtures.duplicate";
    private static final String BROKEN_PKG = "com.link.easyai.starter.engine.builder.fixtures.broken";

    private ApplicationContext applicationContext;
    private ExtractionOverrideStore overrideStore;
    private ObjectProvider<ExtractionOverrideStore> storeProvider;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getEnvironment()).thenReturn(new StandardEnvironment());
        when(applicationContext.getClassLoader()).thenReturn(Thread.currentThread().getContextClassLoader());

        overrideStore = mock(ExtractionOverrideStore.class);
        storeProvider = Mockito.mock(ObjectProvider.class);
        when(storeProvider.getIfAvailable()).thenReturn(overrideStore);
    }

    private AnnotationAiTaskConfigService newService(String... basePackages) {
        return newService(new AiAnnotationScanner(), basePackages);
    }

    private AnnotationAiTaskConfigService newService(AiAnnotationScanner scanner, String... basePackages) {
        AiTaskProperties properties = new AiTaskProperties();
        properties.getAnnotation().setBasePackages(basePackages);
        return new AnnotationAiTaskConfigService(
                scanner,
                new AiTaskConfigBuilder(),
                testResolver(),
                properties,
                storeProvider,
                applicationContext);
    }

    private StaticBeanResolver testResolver() {
        return new StaticBeanResolver(
                new FixtureAction(),
                new NotEmptyValidator(),
                new EnumValidator());
    }

    private void refresh(AnnotationAiTaskConfigService service) {
        service.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
    }

    private FieldExtractionOverrides overridesFor(String taskType, String fieldCode, String newRule) {
        return FieldExtractionOverrides.builder()
                .taskType(taskType)
                .fields(Map.of(fieldCode, ExtractionConfig.builder().rules(java.util.List.of(newRule)).build()))
                .build();
    }

    // ---------- routing: annotation first, DB overrides merged ----------

    @Test
    @DisplayName("无数据库覆盖时注解任务返回注解配置（version=1）")
    void annotationTaskServedFromCodeWhenNoOverrides() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        AiTaskConfig config = service.getLatestPublished("FIXTURE_TASK_A", null);

        assertNotNull(config);
        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        assertEquals(1, config.getVersion());
        assertEquals("任务甲", config.getName());
        assertEquals(5, config.getFields().size());
        // 无覆盖：不修改注解原配置
        verify(overrideStore).getPublishedOverrides("FIXTURE_TASK_A", null);
        assertEquals("规则一", config.getField("customerName").getExtraction().getRules().get(0));
    }

    @Test
    @DisplayName("数据库覆盖按字段合并进注解配置，未覆盖字段保持注解默认")
    void databaseOverrideMergedPerField() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        FieldExtractionOverrides overrides = overridesFor("FIXTURE_TASK_A", "customerName", "新规则X");
        overrides.setVersion(2);
        when(overrideStore.getPublishedOverrides("FIXTURE_TASK_A", "T1")).thenReturn(overrides);

        AiTaskConfig merged = service.getLatestPublished("FIXTURE_TASK_A", "T1");

        assertEquals(2, merged.getVersion()); // 覆盖版本生效
        ExtractionConfig customerName = merged.getField("customerName").getExtraction();
        assertEquals("新规则X", customerName.getRules().get(0));
        // 未覆盖字段保持注解默认
        ExtractionConfig priority = merged.getField("priority").getExtraction();
        assertEquals("优先级描述", priority.getDescription());
        // 注解缓存不被污染：原配置仍是注解默认
        AiTaskConfig annotation = service.getAnnotationConfigs().get("FIXTURE_TASK_A");
        assertEquals("规则一", annotation.getField("customerName").getExtraction().getRules().get(0));
    }

    @Test
    @DisplayName("租户维度透传：getLatestPublished 以 (taskType, tenantId) 解析覆盖")
    void tenantIdPassedToOverrideStore() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        service.getLatestPublished("FIXTURE_TASK_A", "TENANT-X");

        verify(overrideStore).getPublishedOverrides("FIXTURE_TASK_A", "TENANT-X");
    }

    @Test
    @DisplayName("未注解声明的任务抛 ConfigNotFoundException（任务只来自注解）")
    void unregisteredTaskThrows() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished("DB_TASK", null));
        assertThrows(ConfigNotFoundException.class, () -> service.get("DB_TASK", 5, null));
    }

    @Test
    @DisplayName("getLatestVersion：无覆盖返回 1；有覆盖返回覆盖版本；未注解任务返回 null")
    void latestVersionRouting() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        assertEquals(1, service.getLatestVersion("FIXTURE_TASK_A", null));

        when(overrideStore.getLatestVersion("FIXTURE_TASK_A", "T1")).thenReturn(7);
        assertEquals(7, service.getLatestVersion("FIXTURE_TASK_A", "T1"));

        assertNull(service.getLatestVersion("DB_TASK", null));
    }

    @Test
    @DisplayName("在途任务恢复：按绑定版本 + 租户解析覆盖并合并")
    void inFlightTaskMergesBoundVersion() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        FieldExtractionOverrides overrides = overridesFor("FIXTURE_TASK_A", "customerName", "v3规则");
        when(overrideStore.getOverrides("FIXTURE_TASK_A", 3, null)).thenReturn(overrides);

        AiTaskConfig config = service.get("FIXTURE_TASK_A", 3, null);

        assertEquals(3, config.getVersion());
        assertEquals("v3规则", config.getField("customerName").getExtraction().getRules().get(0));
    }

    // ---------- saveDraft: now allowed for annotation tasks, with validation ----------

    @Test
    @DisplayName("saveDraft 允许注解任务（存字段覆盖），并委托 override store")
    void saveDraftAllowedForAnnotationTask() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        FieldExtractionOverrides overrides = overridesFor("FIXTURE_TASK_A", "customerName", "规则");
        service.saveDraft("FIXTURE_TASK_A", null, overrides);

        verify(overrideStore).saveDraft("FIXTURE_TASK_A", null, overrides);
    }

    @Test
    @DisplayName("saveDraft 覆盖引用了不存在的字段时抛出 ConfigValidationException")
    void saveDraftRejectsUnknownField() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        FieldExtractionOverrides overrides = FieldExtractionOverrides.builder()
                .taskType("FIXTURE_TASK_A")
                .fields(Map.of("noSuchField", ExtractionConfig.builder().rules(java.util.List.of("x")).build()))
                .build();

        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> service.saveDraft("FIXTURE_TASK_A", null, overrides));
        assertTrue(e.getMessage().contains("noSuchField"), e.getMessage());
        verify(overrideStore, never()).saveDraft(any(), any(), any());
    }

    @Test
    @DisplayName("saveDraft 对未注解任务抛 ConfigValidationException")
    void saveDraftRejectsUnregisteredTask() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        assertThrows(ConfigValidationException.class,
                () -> service.saveDraft("DB_TASK", null, overridesFor("DB_TASK", "x", "r")));
    }

    // ---------- startup validation ----------

    @Test
    @DisplayName("重复 taskType：启动失败并指认两个冲突类")
    void duplicateTaskTypeFailsStartup() {
        AnnotationAiTaskConfigService service = newService(DUPLICATE_PKG);

        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> refresh(service));

        String message = e.getMessage();
        assertTrue(message.contains("DUP_TASK"), message);
        assertTrue(message.contains("DuplicateTaskOne"), message);
        assertTrue(message.contains("DuplicateTaskTwo"), message);
    }

    @Test
    @DisplayName("损坏声明：启动失败并收集全部错误")
    void brokenDeclarationsFailStartup() {
        AnnotationAiTaskConfigService service = newService(BROKEN_PKG);

        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> refresh(service));

        String message = e.getMessage();
        assertTrue(message.contains("UnregisteredValidator"), message);
        assertTrue(message.contains("noSuchField"), message);
        assertTrue(message.contains("不能依赖自己"), message);
        assertTrue(message.contains("类型不被支持"), message);
        assertTrue(message.contains("type 不能为空"), message);
    }

    // ---------- lifecycle of the build itself ----------

    @Test
    @DisplayName("外来上下文的刷新事件被忽略；自身事件只构建一次")
    void ignoresForeignContextEventsAndBuildsOnce() {
        AiAnnotationScanner scanner = mock(AiAnnotationScanner.class);
        when(scanner.scan(any(), any(), any())).thenReturn(java.util.List.of());
        AnnotationAiTaskConfigService service = newService(scanner, VALID_PKG);

        ApplicationContext foreign = mock(ApplicationContext.class);
        service.onApplicationEvent(new ContextRefreshedEvent(foreign));
        verify(scanner, never()).scan(any(), any(), any());

        refresh(service);
        refresh(service);
        verify(scanner).scan(any(), any(), any());
    }

    @Test
    @DisplayName("未触发刷新事件时懒加载兜底构建")
    void lazyBuildWithoutRefreshEvent() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);

        AiTaskConfig config = service.getLatestPublished("FIXTURE_TASK_A", null);

        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        Map<String, AiTaskConfig> all = service.getAnnotationConfigs();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("无覆盖存储服务时注解配置原样可用（纯注解模式）")
    void behavesWithoutOverrideStore() {
        ObjectProvider<ExtractionOverrideStore> empty = Mockito.mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        AiTaskProperties properties = new AiTaskProperties();
        properties.getAnnotation().setBasePackages(new String[]{VALID_PKG});
        AnnotationAiTaskConfigService service = new AnnotationAiTaskConfigService(
                new AiAnnotationScanner(), new AiTaskConfigBuilder(), testResolver(),
                properties, empty, applicationContext);
        refresh(service);

        assertEquals(1, service.getLatestVersion("FIXTURE_TASK_A", null));
        AiTaskConfig config = service.getLatestPublished("FIXTURE_TASK_A", null);
        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished("UNKNOWN_TASK", null));
    }
}
