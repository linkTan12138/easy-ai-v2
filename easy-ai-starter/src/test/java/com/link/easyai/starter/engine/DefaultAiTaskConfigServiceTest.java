package com.link.easyai.starter.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.mapper.AiTaskConfigRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultAiTaskConfigService}: extraction-override storage with
 * tenant-first / global-fallback resolution, version lifecycle, JSON parsing and
 * taskType consistency validation.
 */
class DefaultAiTaskConfigServiceTest {

    private AiTaskConfigRecordMapper mapper;
    private DefaultAiTaskConfigService service;

    private static final String CONFIG_JSON = """
            {
              "taskType": "ORDER_UPDATE",
              "fields": {
                "channel": { "description": "渠道", "rules": ["规则A"] }
              }
            }
            """;

    @BeforeEach
    void setUp() {
        mapper = mock(AiTaskConfigRecordMapper.class);
        service = new DefaultAiTaskConfigService(mapper, new ObjectMapper());
    }

    private AiTaskConfigRecord record(String taskType, String tenantId, int version, String status) {
        AiTaskConfigRecord r = new AiTaskConfigRecord();
        r.setId((long) version);
        r.setTaskType(taskType);
        r.setTenantId(tenantId);
        r.setVersion(version);
        r.setName("name-" + version);
        r.setConfigJson(CONFIG_JSON);
        r.setStatus(status);
        return r;
    }

    // ---------- read: tenant first, global fallback ----------

    @Test
    @DisplayName("getPublishedOverrides：租户作用域有发布时返回租户覆盖")
    void publishedOverridesPrefersTenantScope() {
        when(mapper.selectOne(any())).thenReturn(record("ORDER_UPDATE", "T1", 3,
                AiTaskConfigRecord.STATUS_PUBLISHED));

        FieldExtractionOverrides overrides = service.getPublishedOverrides("ORDER_UPDATE", "T1");

        assertNotNull(overrides);
        assertEquals("ORDER_UPDATE", overrides.getTaskType());
        assertEquals(3, overrides.getVersion()); // DB identity wins over JSON
        assertEquals("规则A", overrides.getFields().get("channel").getRules().get(0));
    }

    @Test
    @DisplayName("getPublishedOverrides：租户无覆盖时回退全局作用域")
    void publishedOverridesFallsBackToGlobal() {
        // 第一次查询（租户 T1）返回 null → 回退查询全局
        when(mapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(record("ORDER_UPDATE", null, 2, AiTaskConfigRecord.STATUS_PUBLISHED));

        FieldExtractionOverrides overrides = service.getPublishedOverrides("ORDER_UPDATE", "T1");

        assertNotNull(overrides);
        assertEquals(2, overrides.getVersion());
        verify(mapper, times(2)).selectOne(any());
    }

    @Test
    @DisplayName("getPublishedOverrides：租户与全局都无覆盖时返回 null（使用注解默认）")
    void publishedOverridesReturnsNullWhenNone() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertNull(service.getPublishedOverrides("ORDER_UPDATE", "T1"));
        assertNull(service.getPublishedOverrides("ORDER_UPDATE", null));
    }

    @Test
    @DisplayName("getOverrides：指定版本任意状态，租户优先全局兜底")
    void getOverridesAnyStatusTenantFirst() {
        when(mapper.selectOne(any()))
                .thenReturn(null) // 租户无
                .thenReturn(record("ORDER_UPDATE", null, 1, AiTaskConfigRecord.STATUS_DISABLED));

        FieldExtractionOverrides overrides = service.getOverrides("ORDER_UPDATE", 1, "T1");

        assertNotNull(overrides);
        assertEquals(1, overrides.getVersion());
    }

    @Test
    @DisplayName("getLatestVersion：租户优先，全局兜底，无发布返回 null")
    void latestVersionResolution() {
        when(mapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(record("ORDER_UPDATE", null, 5, AiTaskConfigRecord.STATUS_PUBLISHED));

        assertEquals(5, service.getLatestVersion("ORDER_UPDATE", "T1"));
        assertNull(service.getLatestVersion("NONE", null));
    }

    @Test
    @DisplayName("非法参数直接抛出异常")
    void invalidArgumentsThrow() {
        assertThrows(ConfigNotFoundException.class, () -> service.getPublishedOverrides(null, null));
        assertThrows(ConfigNotFoundException.class, () -> service.getPublishedOverrides(" ", null));
        assertThrows(ConfigNotFoundException.class, () -> service.getOverrides("X", null, null));
        assertThrows(ConfigNotFoundException.class, () -> service.getOverrides(null, 1, null));
    }

    // ---------- JSON parsing & validation ----------

    @Test
    @DisplayName("config_json.taskType 与表行不一致时抛 ConfigNotFoundException（防串任务）")
    void taskTypeMismatchThrows() {
        AiTaskConfigRecord r = record("OTHER_TYPE", null, 7, AiTaskConfigRecord.STATUS_PUBLISHED);
        when(mapper.selectOne(any())).thenReturn(r);

        assertThrows(ConfigNotFoundException.class, () -> service.getPublishedOverrides("OTHER_TYPE", null));
    }

    @Test
    @DisplayName("configJson 为空或非法时抛 ConfigNotFoundException")
    void malformedJsonThrows() {
        AiTaskConfigRecord r = record("ORDER_UPDATE", null, 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        r.setConfigJson("{ not valid json");
        when(mapper.selectOne(any())).thenReturn(r);

        assertThrows(ConfigNotFoundException.class, () -> service.getPublishedOverrides("ORDER_UPDATE", null));
    }

    @Test
    @DisplayName("PUBLISHED 覆盖解析结果被缓存（第二次不再查库）")
    void publishedOverridesCached() {
        when(mapper.selectOne(any())).thenReturn(record("ORDER_UPDATE", null, 2,
                AiTaskConfigRecord.STATUS_PUBLISHED));

        service.getOverrides("ORDER_UPDATE", 2, null);
        service.getOverrides("ORDER_UPDATE", 2, null);

        verify(mapper, times(1)).selectOne(any());
    }

    // ---- Lifecycle management ----

    @Test
    @DisplayName("saveDraft：自动分配版本号、按租户作用域插入记录并序列化覆盖集")
    void saveDraftNewConfigAutoVersion() {
        when(mapper.selectList(any())).thenReturn(List.of());

        FieldExtractionOverrides overrides = FieldExtractionOverrides.builder()
                .taskType("DEMO")
                .fields(Map.of("phone", ExtractionConfig.builder().rules(List.of("r")).build()))
                .build();

        ArgumentCaptor<AiTaskConfigRecord> captor = ArgumentCaptor.forClass(AiTaskConfigRecord.class);
        AiTaskConfigRecord result = service.saveDraft("DEMO", "T1", overrides);

        verify(mapper).insert(captor.capture());
        assertEquals("DEMO", result.getTaskType());
        assertEquals("T1", result.getTenantId());
        assertEquals(1, result.getVersion());
        assertEquals(AiTaskConfigRecord.STATUS_DRAFT, result.getStatus());
        assertTrue(captor.getValue().getConfigJson().contains("phone"));
    }

    @Test
    @DisplayName("saveDraft：全局作用域 tenantId 归一为 null")
    void saveDraftNormalizesBlankTenantToNull() {
        when(mapper.selectList(any())).thenReturn(List.of());

        service.saveDraft("DEMO", "  ", FieldExtractionOverrides.builder().taskType("DEMO").build());

        ArgumentCaptor<AiTaskConfigRecord> captor = ArgumentCaptor.forClass(AiTaskConfigRecord.class);
        verify(mapper).insert(captor.capture());
        assertNull(captor.getValue().getTenantId());
    }

    @Test
    @DisplayName("saveDraft：指定版本已有 DRAFT 时更新而非插入")
    void saveDraftUpdatesExistingDraft() {
        AiTaskConfigRecord existing = record("DEMO", "T1", 1, AiTaskConfigRecord.STATUS_DRAFT);
        when(mapper.selectOne(any())).thenReturn(existing);

        FieldExtractionOverrides overrides = FieldExtractionOverrides.builder()
                .taskType("DEMO").version(1)
                .fields(Map.of("phone", ExtractionConfig.builder().rules(List.of("新规则")).build()))
                .build();

        AiTaskConfigRecord result = service.saveDraft("DEMO", "T1", overrides);

        verify(mapper).updateById(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("publish：同租户作用域下旧 PUBLISHED 自动 DISABLED")
    void publishDisablesOldPublishedInSameScope() {
        AiTaskConfigRecord draft = record("DEMO", "T1", 2, AiTaskConfigRecord.STATUS_DRAFT);
        AiTaskConfigRecord oldPublished = record("DEMO", "T1", 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        when(mapper.selectOne(any())).thenReturn(draft);
        when(mapper.selectList(any())).thenReturn(List.of(oldPublished));

        AiTaskConfigRecord result = service.publish("DEMO", 2, "T1");

        assertEquals(AiTaskConfigRecord.STATUS_PUBLISHED, result.getStatus());
        assertEquals(AiTaskConfigRecord.STATUS_DISABLED, oldPublished.getStatus());
    }

    @Test
    @DisplayName("publish 不存在的配置或非 DRAFT 时抛出 ConfigNotFoundException")
    void publishInvalidThrows() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(ConfigNotFoundException.class, () -> service.publish("DEMO", 99, null));

        when(mapper.selectOne(any())).thenReturn(record("DEMO", null, 1, AiTaskConfigRecord.STATUS_PUBLISHED));
        assertThrows(ConfigNotFoundException.class, () -> service.publish("DEMO", 1, null));
    }

    @Test
    @DisplayName("disable 将 PUBLISHED 转为 DISABLED；不存在时抛异常")
    void disableLifecycle() {
        when(mapper.selectOne(any())).thenReturn(record("DEMO", null, 1, AiTaskConfigRecord.STATUS_PUBLISHED));
        AiTaskConfigRecord result = service.disable("DEMO", 1, null);
        assertEquals(AiTaskConfigRecord.STATUS_DISABLED, result.getStatus());
        verify(mapper).updateById(any());

        when(mapper.selectOne(any())).thenReturn(null);
        assertThrows(ConfigNotFoundException.class, () -> service.disable("DEMO", 99, null));
    }

    @Test
    @DisplayName("list 按任务类型 + 租户作用域查询并按版本降序返回")
    void listScopedByTenant() {
        when(mapper.selectList(any())).thenReturn(List.of(
                record("DEMO", "T1", 2, AiTaskConfigRecord.STATUS_PUBLISHED),
                record("DEMO", "T1", 1, AiTaskConfigRecord.STATUS_DRAFT)));

        List<AiTaskConfigRecord> result = service.list("DEMO", "T1");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersion());
        assertEquals(1, result.get(1).getVersion());
    }
}
