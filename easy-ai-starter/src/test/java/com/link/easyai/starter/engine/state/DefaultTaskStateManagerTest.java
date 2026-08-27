package com.link.easyai.starter.engine.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.mapper.TbChatSessionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultTaskStateManager}: load from DB, save to DB,
 * JSON serialization/deserialization, and graceful degradation.
 */
class DefaultTaskStateManagerTest {

    private TbChatSessionTaskMapper taskMapper;
    private ObjectMapper objectMapper;
    private DefaultTaskStateManager manager;

    @BeforeEach
    void setUp() {
        taskMapper = mock(TbChatSessionTaskMapper.class);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        manager = new DefaultTaskStateManager(taskMapper, objectMapper);
    }

    // ---------- load ----------

    @Test
    @DisplayName("taskId 为 null 时创建新 state")
    void loadNullTaskIdCreatesFreshState() {
        TaskState state = manager.load(null, "ORDER_UPDATE", 1);

        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
        assertEquals("ORDER_UPDATE", state.getTaskType());
        assertEquals(1, state.getConfigVersion());
        assertTrue(state.getFields().isEmpty());
    }

    @Test
    @DisplayName("taskId 为空字符串时创建新 state")
    void loadBlankTaskIdCreatesFreshState() {
        TaskState state = manager.load("  ", "ORDER_UPDATE", 1);

        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
    }

    @Test
    @DisplayName("taskId 非数字时创建新 state")
    void loadNonNumericTaskIdCreatesFreshState() {
        TaskState state = manager.load("not-a-number", "ORDER_UPDATE", 1);

        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
    }

    @Test
    @DisplayName("taskId 存在但 DB 无记录 -> 创建新 state")
    void loadNoDbRecordCreatesFreshState() {
        when(taskMapper.selectById(100L)).thenReturn(null);

        TaskState state = manager.load("100", "ORDER_UPDATE", 2);

        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
        assertEquals("ORDER_UPDATE", state.getTaskType());
        assertEquals(2, state.getConfigVersion());
    }

    @Test
    @DisplayName("从 DB 加载已有 state JSON")
    void loadExistingStateJson() throws Exception {
        TaskState original = TaskState.builder()
                .taskId("100")
                .taskType("ORDER_UPDATE")
                .configVersion(1)
                .status(TaskStatus.COLLECTING)
                .fields(new java.util.HashMap<>())
                .context(new java.util.HashMap<>())
                .build();
        original.putField("customerNos", FieldState.builder()
                .field("customerNos")
                .status(FieldStatus.VALID)
                .value("ABC123")
                .rawValue("ABC123")
                .build());

        String json = objectMapper.writeValueAsString(original);

        TbChatSessionTask entity = new TbChatSessionTask();
        entity.setId(100L);
        entity.setAiTaskState(json);
        entity.setConfigVersion(1);
        when(taskMapper.selectById(100L)).thenReturn(entity);

        TaskState loaded = manager.load("100", "ORDER_UPDATE", 1);

        assertEquals(TaskStatus.COLLECTING, loaded.getStatus());
        assertEquals("ORDER_UPDATE", loaded.getTaskType());
        assertEquals("100", loaded.getTaskId());
        assertNotNull(loaded.getField("customerNos"));
        assertEquals(FieldStatus.VALID, loaded.getField("customerNos").getStatus());
        assertEquals("ABC123", loaded.getField("customerNos").getValue());
    }

    @Test
    @DisplayName("DB 有记录但 state JSON 为空 -> 创建新 state")
    void loadNullStateJsonCreatesFreshState() {
        TbChatSessionTask entity = new TbChatSessionTask();
        entity.setId(200L);
        entity.setAiTaskState(null);
        entity.setConfigVersion(3);
        when(taskMapper.selectById(200L)).thenReturn(entity);

        TaskState state = manager.load("200", "ORDER_UPDATE", 3);

        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
        assertEquals(3, state.getConfigVersion());
    }

    @Test
    @DisplayName("state JSON 损坏 -> 优雅降级创建新 state")
    void loadCorruptStateJsonGracefulFallback() {
        TbChatSessionTask entity = new TbChatSessionTask();
        entity.setId(300L);
        entity.setAiTaskState("{this is not valid json!!!");
        entity.setConfigVersion(1);
        when(taskMapper.selectById(300L)).thenReturn(entity);

        TaskState state = manager.load("300", "ORDER_UPDATE", 1);

        // Should not throw, should return a fresh state
        assertEquals(TaskStatus.INITIALIZED, state.getStatus());
    }

    @Test
    @DisplayName("从 DB 加载 state 时 taskId 缺失则自动填充")
    void loadSetsTaskIdIfMissing() throws Exception {
        // Create a state JSON without taskId
        String json = "{\"taskType\":\"ORDER_UPDATE\",\"configVersion\":1,\"status\":\"COLLECTING\",\"fields\":{}}";

        TbChatSessionTask entity = new TbChatSessionTask();
        entity.setId(400L);
        entity.setAiTaskState(json);
        when(taskMapper.selectById(400L)).thenReturn(entity);

        TaskState state = manager.load("400", "ORDER_UPDATE", 1);

        assertEquals("400", state.getTaskId());
    }

    // ---------- save ----------

    @Test
    @DisplayName("save null state 不报错")
    void saveNullStateNoOp() {
        assertDoesNotThrow(() -> manager.save(null));
    }

    @Test
    @DisplayName("save taskId 为 null 不报错")
    void saveNullTaskIdNoOp() {
        TaskState state = TaskState.builder()
                .taskId(null)
                .taskType("ORDER_UPDATE")
                .status(TaskStatus.COLLECTING)
                .build();

        assertDoesNotThrow(() -> manager.save(state));
    }

    @Test
    @DisplayName("save taskId 非数字不报错")
    void saveNonNumericTaskIdNoOp() {
        TaskState state = TaskState.builder()
                .taskId("not-a-number")
                .taskType("ORDER_UPDATE")
                .status(TaskStatus.COLLECTING)
                .build();

        assertDoesNotThrow(() -> manager.save(state));
    }

    @Test
    @DisplayName("save 新任务时 insert 新记录")
    void saveNewRecordInserts() {
        when(taskMapper.selectById(500L)).thenReturn(null);

        TaskState state = TaskState.builder()
                .taskId("500")
                .taskType("ORDER_UPDATE")
                .configVersion(1)
                .status(TaskStatus.COLLECTING)
                .fields(new java.util.HashMap<>())
                .context(new java.util.HashMap<>())
                .build();

        manager.save(state);

        verify(taskMapper).insert(any(TbChatSessionTask.class));
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("save 已有任务时 update 记录")
    void saveExistingRecordUpdates() {
        TbChatSessionTask existing = new TbChatSessionTask();
        existing.setId(600L);
        when(taskMapper.selectById(600L)).thenReturn(existing);

        TaskState state = TaskState.builder()
                .taskId("600")
                .taskType("ORDER_UPDATE")
                .configVersion(2)
                .status(TaskStatus.COMPLETED)
                .fields(new java.util.HashMap<>())
                .context(new java.util.HashMap<>())
                .build();

        manager.save(state);

        verify(taskMapper).updateById(argThat(entity -> {
            TbChatSessionTask t = (TbChatSessionTask) entity;
            return t.getAiTaskState() != null
                    && t.getStatus() == 5 // COMPLETED -> 5
                    && "ORDER_UPDATE".equals(t.getTaskType())
                    && t.getConfigVersion() == 2;
        }));
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("save 时正确序列化 state JSON")
    void saveSerializesStateJson() throws Exception {
        when(taskMapper.selectById(700L)).thenReturn(null);

        TaskState state = TaskState.builder()
                .taskId("700")
                .taskType("ORDER_UPDATE")
                .configVersion(1)
                .status(TaskStatus.COLLECTING)
                .fields(new java.util.HashMap<>())
                .context(new java.util.HashMap<>())
                .build();
        state.putField("f1", FieldState.builder()
                .field("f1").status(FieldStatus.VALID).value("v1").build());

        manager.save(state);

        ArgumentCaptor<TbChatSessionTask> captor = ArgumentCaptor.forClass(TbChatSessionTask.class);
        verify(taskMapper).insert(captor.capture());

        String json = captor.getValue().getAiTaskState();
        assertNotNull(json);
        assertTrue(json.contains("ORDER_UPDATE"));
        assertTrue(json.contains("COLLECTING"));
        assertTrue(json.contains("f1"));
        assertTrue(json.contains("v1"));

        // Verify round-trip
        TaskState restored = objectMapper.readValue(json, TaskState.class);
        assertEquals(TaskStatus.COLLECTING, restored.getStatus());
        assertEquals("v1", restored.getField("f1").getValue());
    }

    // ---------- status mapping ----------

    @Test
    @DisplayName("TaskStatus.COMPLETED -> int 5 (已完成)")
    void statusCompletedMapsTo5() {
        assertStatusMapping(TaskStatus.COMPLETED, 5);
    }

    @Test
    @DisplayName("TaskStatus.FAILED -> int 3 (失败)")
    void statusFailedMapsTo3() {
        assertStatusMapping(TaskStatus.FAILED, 3);
    }

    @Test
    @DisplayName("TaskStatus.CANCELLED -> int 4 (已停止)")
    void statusCancelledMapsTo4() {
        assertStatusMapping(TaskStatus.CANCELLED, 4);
    }

    @Test
    @DisplayName("TaskStatus.COLLECTING -> int 2 (处理中)")
    void statusCollectingMapsTo2() {
        assertStatusMapping(TaskStatus.COLLECTING, 2);
    }

    @Test
    @DisplayName("TaskStatus.INITIALIZED -> int 0 (待处理)")
    void statusInitializedMapsTo0() {
        assertStatusMapping(TaskStatus.INITIALIZED, 0);
    }

    private void assertStatusMapping(TaskStatus status, int expectedInt) {
        when(taskMapper.selectById(800L)).thenReturn(null);

        TaskState state = TaskState.builder()
                .taskId("800")
                .taskType("TEST")
                .configVersion(1)
                .status(status)
                .fields(new java.util.HashMap<>())
                .context(new java.util.HashMap<>())
                .build();

        manager.save(state);

        ArgumentCaptor<TbChatSessionTask> captor = ArgumentCaptor.forClass(TbChatSessionTask.class);
        verify(taskMapper).insert(captor.capture());

        assertEquals(expectedInt, captor.getValue().getStatus());
    }
}
