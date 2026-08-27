package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.builder.AiAnnotationScanner;
import com.link.easyai.starter.engine.builder.AiTaskConfigBuilder;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;
import com.link.easyai.starter.engine.builder.fixtures.StaticBeanResolver;
import com.link.easyai.starter.engine.config.AiTaskConfig;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AnnotationAiTaskConfigService}: annotation-first routing
 * (one config source per task type), database fallback, in-flight version
 * routing, and startup validation (duplicate taskType, broken declarations).
 */
@SuppressWarnings("unchecked")
class AnnotationAiTaskConfigServiceTest {

    private static final String VALID_PKG = "com.link.easyai.starter.engine.builder.fixtures.valid";
    private static final String DUPLICATE_PKG = "com.link.easyai.starter.engine.builder.fixtures.duplicate";
    private static final String BROKEN_PKG = "com.link.easyai.starter.engine.builder.fixtures.broken";

    private ApplicationContext applicationContext;
    private DefaultAiTaskConfigService databaseService;
    private ObjectProvider<DefaultAiTaskConfigService> databaseProvider;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getEnvironment()).thenReturn(new StandardEnvironment());
        when(applicationContext.getClassLoader()).thenReturn(Thread.currentThread().getContextClassLoader());

        databaseService = mock(DefaultAiTaskConfigService.class);
        databaseProvider = Mockito.mock(ObjectProvider.class);
        when(databaseProvider.getIfAvailable()).thenReturn(databaseService);
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
                databaseProvider,
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

    // ---------- routing: annotation first ----------

    @Test
    @DisplayName("注解任务由代码提供配置（version=1），完全不查数据库")
    void annotationTaskServedFromCode() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        AiTaskConfig config = service.getLatestPublished("FIXTURE_TASK_A");

        assertNotNull(config);
        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        assertEquals(1, config.getVersion());
        assertEquals("任务甲", config.getName());
        assertEquals("FIXTURE_ACTION", config.getAction().getType());
        assertEquals(5, config.getFields().size());
        verify(databaseService, never()).getLatestPublished(anyString());

        assertTrue(service.isAnnotationConfigured("FIXTURE_TASK_A"));
        assertTrue(service.getAnnotationConfigs().containsKey("FIXTURE_TASK_B"));
    }

    @Test
    @DisplayName("未注解声明的任务回落到数据库服务")
    void databaseTaskFallsBackToDatabase() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        AiTaskConfig dbConfig = AiTaskConfig.builder()
                .taskType("DB_TASK").version(5).name("数据库任务").build();
        when(databaseService.getLatestPublished("DB_TASK")).thenReturn(dbConfig);

        AiTaskConfig config = service.getLatestPublished("DB_TASK");

        assertEquals("DB_TASK", config.getTaskType());
        assertEquals(5, config.getVersion());
        verify(databaseService).getLatestPublished("DB_TASK");
    }

    @Test
    @DisplayName("在途任务路由：注解任务请求非 1 版本时回落数据库")
    void inFlightTaskResumesAgainstDatabaseVersion() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        AiTaskConfig legacy = AiTaskConfig.builder()
                .taskType("FIXTURE_TASK_A").version(3).name("旧版").build();
        when(databaseService.get("FIXTURE_TASK_A", 3)).thenReturn(legacy);

        assertEquals(legacy, service.get("FIXTURE_TASK_A", 3));
        // version 1 always resolves to the annotation config, never the database
        assertEquals(1, service.get("FIXTURE_TASK_A", 1).getVersion());
        verify(databaseService).get("FIXTURE_TASK_A", 3);
        verify(databaseService, never()).get("FIXTURE_TASK_A", 1);
    }

    @Test
    @DisplayName("getLatestVersion：注解任务恒为 1；DB 任务走数据库；无 DB 返回 null")
    void latestVersionRouting() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        assertEquals(1, service.getLatestVersion("FIXTURE_TASK_A"));

        when(databaseService.getLatestVersion("DB_TASK")).thenReturn(7);
        assertEquals(7, service.getLatestVersion("DB_TASK"));
    }

    @Test
    @DisplayName("无数据库服务时 getLatestVersion 返回 null（契约），读取抛 CONFIG_NOT_FOUND")
    void behavesWithoutDatabaseService() {
        ObjectProvider<DefaultAiTaskConfigService> empty = Mockito.mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        AiTaskProperties properties = new AiTaskProperties();
        properties.getAnnotation().setBasePackages(new String[]{VALID_PKG});
        AnnotationAiTaskConfigService service = new AnnotationAiTaskConfigService(
                new AiAnnotationScanner(), new AiTaskConfigBuilder(), testResolver(),
                properties, empty, applicationContext);
        refresh(service);

        assertNull(service.getLatestVersion("UNKNOWN_TASK"));
        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished("UNKNOWN_TASK"));
        // annotation tasks still work without any database
        assertEquals(1, service.getLatestVersion("FIXTURE_TASK_A"));
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

    @Test
    @DisplayName("saveDraft 拒绝注解声明的 taskType（代码即配置）")
    void saveDraftRejectedForAnnotationTask() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);
        refresh(service);

        AiTaskConfig draft = AiTaskConfig.builder().taskType("FIXTURE_TASK_A").version(2).build();
        assertThrows(ConfigValidationException.class, () -> service.saveDraft(draft));
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
        refresh(service); // second refresh of the same context must not rebuild
        verify(scanner).scan(any(), any(), any());
    }

    @Test
    @DisplayName("未触发刷新事件时懒加载兜底构建")
    void lazyBuildWithoutRefreshEvent() {
        AnnotationAiTaskConfigService service = newService(VALID_PKG);

        // no refresh event fired — direct read triggers the lazy build
        AiTaskConfig config = service.getLatestPublished("FIXTURE_TASK_A");

        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        Map<String, AiTaskConfig> all = service.getAnnotationConfigs();
        assertEquals(2, all.size());
    }
}
