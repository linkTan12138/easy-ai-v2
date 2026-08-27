package com.link.easyai.starter.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.mapper.AiTaskConfigRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultAiTaskConfigService}: version resolution,
 * JSON parsing, caching, and version binding semantics.
 */
class DefaultAiTaskConfigServiceTest {

    private AiTaskConfigRecordMapper mapper;
    private DefaultAiTaskConfigService service;

    private static final String CONFIG_JSON = """
            {
              "taskType": "ORDER_UPDATE",
              "version": 2,
              "name": "运单修改",
              "fields": [
                {"code": "channel", "name": "渠道", "type": "STRING", "required": true}
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        mapper = mock(AiTaskConfigRecordMapper.class);
        service = new DefaultAiTaskConfigService(mapper, new ObjectMapper());
    }

    private AiTaskConfigRecord record(String taskType, int version, String status) {
        AiTaskConfigRecord r = new AiTaskConfigRecord();
        r.setId((long) version);
        r.setTaskType(taskType);
        r.setVersion(version);
        r.setName("name-" + version);
        r.setConfigJson(CONFIG_JSON);
        r.setStatus(status);
        return r;
    }

    @Test
    @DisplayName("getLatestPublished 返回最新 PUBLISHED 版本并解析 JSON")
    void getLatestPublishedParsesJson() {
        when(mapper.selectOne(any())).thenReturn(record("ORDER_UPDATE", 3,
                AiTaskConfigRecord.STATUS_PUBLISHED));

        AiTaskConfig config = service.getLatestPublished("ORDER_UPDATE");

        assertEquals("ORDER_UPDATE", config.getTaskType());
        assertEquals(3, config.getVersion()); // DB identity wins over JSON
        assertEquals(1, config.getFields().size());
        assertEquals("channel", config.getFields().get(0).getCode());
        assertEquals(FieldType.STRING, config.getFields().get(0).getType());
    }

    @Test
    @DisplayName("无 PUBLISHED 配置时抛出 ConfigNotFoundException")
    void noPublishedConfigThrows() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished("X"));
    }

    @Test
    @DisplayName("get(taskType, version) 支持任意状态（绑定版本可继续使用）")
    void getReturnsBoundVersionRegardlessOfStatus() {
        when(mapper.selectOne(any())).thenReturn(record("ORDER_UPDATE", 1,
                AiTaskConfigRecord.STATUS_DISABLED));

        AiTaskConfig config = service.get("ORDER_UPDATE", 1);

        assertEquals(1, config.getVersion());
    }

    @Test
    @DisplayName("指定版本不存在时抛出 ConfigNotFoundException")
    void missingVersionThrows() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(ConfigNotFoundException.class, () -> service.get("ORDER_UPDATE", 99));
    }

    @Test
    @DisplayName("非法参数直接抛出异常")
    void invalidArgumentsThrow() {
        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished(null));
        assertThrows(ConfigNotFoundException.class, () -> service.getLatestPublished(" "));
        assertThrows(ConfigNotFoundException.class, () -> service.get("X", null));
        assertThrows(ConfigNotFoundException.class, () -> service.get(null, 1));
    }

    @Test
    @DisplayName("getLatestVersion 返回最新 PUBLISHED 版本号")
    void getLatestVersionReturnsPublishedVersion() {
        when(mapper.selectList(any())).thenReturn(List.of(record("ORDER_UPDATE", 5,
                AiTaskConfigRecord.STATUS_PUBLISHED)));

        assertEquals(5, service.getLatestVersion("ORDER_UPDATE"));
    }

    @Test
    @DisplayName("无任何 PUBLISHED 版本时 getLatestVersion 返回 null")
    void getLatestVersionNullWhenNone() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertNull(service.getLatestVersion("ORDER_UPDATE"));
        assertNull(service.getLatestVersion(null));
    }

    @Test
    @DisplayName("PUBLISHED 配置解析结果被缓存（第二次不再查库）")
    void publishedConfigCached() {
        when(mapper.selectOne(any())).thenReturn(record("ORDER_UPDATE", 2,
                AiTaskConfigRecord.STATUS_PUBLISHED));

        service.get("ORDER_UPDATE", 2);
        service.get("ORDER_UPDATE", 2);

        verify(mapper, times(1)).selectOne(any());
    }

    @Test
    @DisplayName("configJson 为空时抛出 ConfigNotFoundException")
    void emptyJsonThrows() {
        AiTaskConfigRecord r = record("ORDER_UPDATE", 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        r.setConfigJson(" ");
        when(mapper.selectOne(any())).thenReturn(r);

        assertThrows(ConfigNotFoundException.class, () -> service.get("ORDER_UPDATE", 1));
    }

    @Test
    @DisplayName("configJson 非法时抛出 ConfigNotFoundException")
    void malformedJsonThrows() {
        AiTaskConfigRecord r = record("ORDER_UPDATE", 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        r.setConfigJson("{ not valid json");
        when(mapper.selectOne(any())).thenReturn(r);

        assertThrows(ConfigNotFoundException.class, () -> service.get("ORDER_UPDATE", 1));
    }

    @Test
    @DisplayName("JSON 中缺失的 taskType/version 由 DB 行补齐")
    void dbIdentityWinsOverJson() {
        AiTaskConfigRecord r = record("OTHER_TYPE", 7, AiTaskConfigRecord.STATUS_PUBLISHED);
        // JSON inside says taskType=ORDER_UPDATE version=2; DB row says otherwise
        when(mapper.selectOne(any())).thenReturn(r);

        AiTaskConfig config = service.get("OTHER_TYPE", 7);

        assertEquals("OTHER_TYPE", config.getTaskType());
        assertEquals(7, config.getVersion());
    }

    // ---- Lifecycle management tests ----

    @Test
    @DisplayName("saveDraft 新配置时自动分配版本号并插入记录")
    void saveDraftNewConfigAutoVersion() {
        when(mapper.selectList(any())).thenReturn(List.of());

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("DEMO")
                .name("演示")
                .fields(List.of())
                .build();

        AiTaskConfigRecord result = service.saveDraft(config);

        assertEquals("DEMO", result.getTaskType());
        assertEquals(1, result.getVersion());
        assertEquals(AiTaskConfigRecord.STATUS_DRAFT, result.getStatus());
        verify(mapper).insert(any());
    }

    @Test
    @DisplayName("saveDraft 已有版本时自动分配下一个版本号")
    void saveDraftAutoNextVersion() {
        when(mapper.selectList(any())).thenReturn(List.of(record("DEMO", 3, AiTaskConfigRecord.STATUS_DRAFT)));

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("DEMO")
                .name("演示v4")
                .fields(List.of())
                .build();

        AiTaskConfigRecord result = service.saveDraft(config);

        assertEquals(4, result.getVersion());
    }

    @Test
    @DisplayName("saveDraft 指定版本且已有 DRAFT 时更新而非插入")
    void saveDraftUpdatesExistingDraft() {
        AiTaskConfigRecord existing = record("DEMO", 1, AiTaskConfigRecord.STATUS_DRAFT);
        when(mapper.selectOne(any())).thenReturn(existing);

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("DEMO")
                .version(1)
                .name("更新后的名称")
                .fields(List.of())
                .build();

        AiTaskConfigRecord result = service.saveDraft(config);

        assertEquals("更新后的名称", result.getName());
        verify(mapper).updateById(any());
        verify(mapper, never()).insert(any());
    }

    @Test
    @DisplayName("saveDraft 序列化 configJson 并存储")
    void saveDraftSerializesJson() {
        when(mapper.selectList(any())).thenReturn(List.of());

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("DEMO")
                .name("演示")
                .fields(List.of(FieldDefinition.builder()
                        .code("field1")
                        .name("字段1")
                        .type(FieldType.STRING)
                        .build()))
                .build();

        ArgumentCaptor<AiTaskConfigRecord> captor = ArgumentCaptor.forClass(AiTaskConfigRecord.class);
        service.saveDraft(config);

        verify(mapper).insert(captor.capture());
        assertNotNull(captor.getValue().getConfigJson());
        assertTrue(captor.getValue().getConfigJson().contains("field1"));
    }

    @Test
    @DisplayName("publish 将 DRAFT 转为 PUBLISHED 并设置发布时间")
    void publishDraftToPublished() {
        AiTaskConfigRecord draft = record("DEMO", 1, AiTaskConfigRecord.STATUS_DRAFT);
        when(mapper.selectOne(any())).thenReturn(draft);
        when(mapper.selectList(any())).thenReturn(List.of());

        AiTaskConfigRecord result = service.publish("DEMO", 1);

        assertEquals(AiTaskConfigRecord.STATUS_PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedTime());
        verify(mapper).updateById(any());
    }

    @Test
    @DisplayName("publish 自动禁用同 taskType 的旧 PUBLISHED 版本")
    void publishDisablesOldPublished() {
        AiTaskConfigRecord draft = record("DEMO", 2, AiTaskConfigRecord.STATUS_DRAFT);
        AiTaskConfigRecord oldPublished = record("DEMO", 1, AiTaskConfigRecord.STATUS_PUBLISHED);

        when(mapper.selectOne(any())).thenReturn(draft);
        // First selectList: for finding old published versions
        when(mapper.selectList(any())).thenReturn(List.of(oldPublished));

        service.publish("DEMO", 2);

        assertEquals(AiTaskConfigRecord.STATUS_DISABLED, oldPublished.getStatus());
    }

    @Test
    @DisplayName("publish 不存在的配置时抛出 ConfigNotFoundException")
    void publishNotFoundThrows() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(ConfigNotFoundException.class, () -> service.publish("DEMO", 99));
    }

    @Test
    @DisplayName("publish 非 DRAFT 状态的配置时抛出异常")
    void publishNonDraftThrows() {
        AiTaskConfigRecord published = record("DEMO", 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        when(mapper.selectOne(any())).thenReturn(published);

        assertThrows(ConfigNotFoundException.class, () -> service.publish("DEMO", 1));
    }

    @Test
    @DisplayName("disable 将 PUBLISHED 转为 DISABLED")
    void disablePublishedToDisabled() {
        AiTaskConfigRecord published = record("DEMO", 1, AiTaskConfigRecord.STATUS_PUBLISHED);
        when(mapper.selectOne(any())).thenReturn(published);

        AiTaskConfigRecord result = service.disable("DEMO", 1);

        assertEquals(AiTaskConfigRecord.STATUS_DISABLED, result.getStatus());
        verify(mapper).updateById(any());
    }

    @Test
    @DisplayName("disable 不存在的配置时抛出异常")
    void disableNotFoundThrows() {
        when(mapper.selectOne(any())).thenReturn(null);

        assertThrows(ConfigNotFoundException.class, () -> service.disable("DEMO", 99));
    }

    @Test
    @DisplayName("list 按 taskType 查询并按版本降序返回")
    void listByTaskType() {
        AiTaskConfigRecord v1 = record("DEMO", 1, AiTaskConfigRecord.STATUS_DRAFT);
        AiTaskConfigRecord v2 = record("DEMO", 2, AiTaskConfigRecord.STATUS_PUBLISHED);
        when(mapper.selectList(any())).thenReturn(List.of(v2, v1));

        List<AiTaskConfigRecord> result = service.list("DEMO");

        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getVersion());
        assertEquals(1, result.get(1).getVersion());
    }

    @Test
    @DisplayName("list 传入 null 时返回所有配置")
    void listAll() {
        when(mapper.selectList(any())).thenReturn(List.of());

        List<AiTaskConfigRecord> result = service.list(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("saveDraft taskType 为空时抛出异常")
    void saveDraftBlankTaskTypeThrows() {
        AiTaskConfig config = AiTaskConfig.builder()
                .taskType(" ")
                .build();

        assertThrows(ConfigNotFoundException.class, () -> service.saveDraft(config));
    }
}
