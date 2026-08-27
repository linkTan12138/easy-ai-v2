package com.link.easyai.starter.engine;

import com.link.easyai.starter.config.LargeLanguageModelHolder;
import com.link.easyai.starter.engine.action.ActionEngine;
import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.completion.CompletionEngine;
import com.link.easyai.starter.engine.config.ActionConfig;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.engine.extraction.ExtractionEngine;
import com.link.easyai.starter.engine.extraction.ExtractionResult;
import com.link.easyai.starter.engine.extraction.FieldSelector;
import com.link.easyai.starter.engine.mapping.MappingEngine;
import com.link.easyai.starter.engine.normalization.NormalizationEngine;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStateManager;
import com.link.easyai.starter.engine.state.TaskStatus;
import com.link.easyai.starter.engine.validation.ValidationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests for {@link DefaultAiTaskEngine}: verifies the 9-step
 * pipeline call order, multi-turn state restoration, branch handling,
 * TaskStatus lifecycle, and the exception safety net.
 */
@ExtendWith(MockitoExtension.class)
class DefaultAiTaskEngineTest {

    @Mock private AiTaskConfigService configService;
    @Mock private TaskStateManager stateManager;
    @Mock private FieldSelector fieldSelector;
    @Mock private ExtractionEngine extractionEngine;
    @Mock private ValidationEngine validationEngine;
    @Mock private NormalizationEngine normalizationEngine;
    @Mock private MappingEngine mappingEngine;
    @Mock private CompletionEngine completionEngine;
    @Mock private ActionEngine actionEngine;
    @Mock private ResponseBuilder responseBuilder;

    private DefaultAiTaskEngine engine;

    private static final String TASK_TYPE = "ORDER_UPDATE";
    private static final String TASK_ID = "42";
    private static final Integer VERSION = 3;

    private AiTaskConfig config;

    @BeforeEach
    void setUp() {
        engine = new DefaultAiTaskEngine(configService, stateManager, fieldSelector,
                extractionEngine, validationEngine, normalizationEngine, mappingEngine,
                completionEngine, actionEngine, responseBuilder, null,null,null);

        config = AiTaskConfig.builder()
                .taskType(TASK_TYPE)
                .version(VERSION)
                .name("运单修改")
                .fields(List.of(
                        FieldDefinition.builder().code("channel").name("渠道")
                                .type(FieldType.STRING).required(true).build(),
                        FieldDefinition.builder().code("receiver").name("收件人")
                                .type(FieldType.STRING).required(true).build()))
                .action(ActionConfig.builder().type("UPDATE_WAYBILL").build())
                .build();
    }

    // ---------- helpers ----------

    /** A persisted mid-conversation state: channel already VALID, receiver missing. */
    private TaskState midConversationState() {
        FieldState channel = FieldState.builder()
                .field("channel").status(FieldStatus.VALID).value("DHL")
                .rawValue("DHL").data(new HashMap<>()).build();
        return TaskState.builder()
                .taskId(TASK_ID).taskType(TASK_TYPE).configVersion(VERSION)
                .status(TaskStatus.COLLECTING)
                .fields(new HashMap<>(Map.of("channel", channel)))
                .context(new HashMap<>())
                .build();
    }

    private TaskState freshState() {
        return TaskState.builder()
                .taskId(TASK_ID).taskType(TASK_TYPE).configVersion(VERSION)
                .status(TaskStatus.INITIALIZED)
                .fields(new HashMap<>())
                .context(new HashMap<>())
                .build();
    }

    private void stubConfigLoading(TaskState state) {
        when(stateManager.load(TASK_ID, TASK_TYPE, null)).thenReturn(state);
        when(stateManager.load(TASK_ID, TASK_TYPE, VERSION)).thenReturn(state);
        // lenient: when the state already carries a bound configVersion the
        // engine never asks for the latest version
        org.mockito.Mockito.lenient().when(configService.getLatestVersion(TASK_TYPE)).thenReturn(VERSION);
        when(configService.get(TASK_TYPE, VERSION)).thenReturn(config);
    }

    // ---------- pipeline order ----------

    @Test
    @DisplayName("管道按序执行：选择→提取→校验→标准化→完成判断→保存")
    void pipelineExecutesInOrder() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        List<FieldDefinition> pending = List.of(config.getFields().get(1));
        when(fieldSelector.select(config, state)).thenReturn(pending);
        when(extractionEngine.extract(eq("收件人张三"), eq(pending), eq(config.getFields()), eq(state), isNull()))
                .thenReturn(ExtractionResult.builder()
                        .fields(Map.of("receiver", "张三")).success(true).build());
        // validation mock updates the state in-place like the real engine
        doAnswerUpdateField(state, "receiver", "张三");
        when(completionEngine.completed(config, state)).thenReturn(false);
        when(responseBuilder.buildNeedMore(config, state)).thenReturn("还需提供：收件人");

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "收件人张三", null);

        InOrder inOrder = inOrder(fieldSelector, extractionEngine, validationEngine,
                normalizationEngine, completionEngine, stateManager);
        inOrder.verify(fieldSelector).select(config, state);
        inOrder.verify(extractionEngine).extract(anyString(), any(), any(), any(), any());
        inOrder.verify(validationEngine).validate(any(), any(), any(), any());
        inOrder.verify(normalizationEngine).normalize(any(), any());
        inOrder.verify(completionEngine).completed(config, state);
        inOrder.verify(stateManager).save(state);

        assertFalse(resp.isCompleted());
        assertEquals("还需提供：收件人", resp.getMessage());
        assertEquals(TaskStatus.COLLECTING, state.getStatus());
        // action pipeline not touched
        verify(mappingEngine, never()).assemble(any(), any(), any());
        verify(actionEngine, never()).execute(any(), any(), any(), any());
    }

    private void doAnswerUpdateField(TaskState state, String code, Object value) {
        org.mockito.Mockito.doAnswer(inv -> {
            state.putField(code, FieldState.builder()
                    .field(code).status(FieldStatus.VALID).value(value)
                    .rawValue(value).data(new HashMap<>()).build());
            return null;
        }).when(validationEngine).validate(any(), any(), any(), any());
    }

    // ---------- status lifecycle ----------

    @Test
    @DisplayName("新任务进入管道后状态从 INITIALIZED 翻转为 COLLECTING")
    void freshTaskTransitionsToCollecting() {
        TaskState state = freshState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of(config.getFields().get(0)));
        when(extractionEngine.extract(anyString(), any(), any(), any(), any()))
                .thenReturn(ExtractionResult.builder().fields(Map.of()).success(true).build());
        when(completionEngine.completed(config, state)).thenReturn(false);
        when(responseBuilder.buildNeedMore(config, state)).thenReturn("msg");

        engine.execute(TASK_TYPE, TASK_ID, "hi", null);

        assertEquals(TaskStatus.COLLECTING, state.getStatus());
    }

    @Test
    @DisplayName("本轮收集完成后执行动作：READY→EXECUTING→COMPLETED，返回 done")
    void completedTurnExecutesAction() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of(config.getFields().get(1)));
        when(extractionEngine.extract(anyString(), any(), any(), any(), any()))
                .thenReturn(ExtractionResult.builder()
                        .fields(Map.of("receiver", "张三")).success(true).build());
        doAnswerUpdateField(state, "receiver", "张三");
        when(completionEngine.completed(config, state)).thenReturn(true);
        when(mappingEngine.assemble(config, state, null))
                .thenReturn(Map.of("receiver", "张三"));
        when(actionEngine.execute(eq(config), eq(state), any(), isNull()))
                .thenReturn(ActionResult.success("运单已更新", "WB-1"));
        when(responseBuilder.buildDone(any())).thenReturn("运单已更新");

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "收件人张三", null);

        assertTrue(resp.isCompleted());
        assertEquals("运单已更新", resp.getMessage());
        assertNotNull(resp.getActionResult());
        assertEquals(TaskStatus.COMPLETED, state.getStatus());
        // final state persisted with COMPLETED
        ArgumentCaptor<TaskState> captor = ArgumentCaptor.forClass(TaskState.class);
        verify(stateManager).save(captor.capture());
        assertEquals(TaskStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("动作执行失败：状态回到 COLLECTING 保留进度，返回 needMore")
    void actionFailureKeepsProgressForRetry() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of(config.getFields().get(1)));
        when(extractionEngine.extract(anyString(), any(), any(), any(), any()))
                .thenReturn(ExtractionResult.builder()
                        .fields(Map.of("receiver", "张三")).success(true).build());
        doAnswerUpdateField(state, "receiver", "张三");
        when(completionEngine.completed(config, state)).thenReturn(true);
        when(mappingEngine.assemble(config, state, null)).thenReturn(Map.of());
        when(actionEngine.execute(eq(config), eq(state), any(), isNull()))
                .thenReturn(ActionResult.fail("ACTION_ERROR", "下游服务超时"));

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "收件人张三", null);

        assertFalse(resp.isCompleted());
        assertEquals("下游服务超时", resp.getMessage());
        assertEquals(TaskStatus.COLLECTING, state.getStatus());
        verify(stateManager).save(state);
    }

    // ---------- branches ----------

    @Test
    @DisplayName("提取失败：保存状态并返回错误提示，不执行校验")
    void extractionFailureShortCircuits() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of(config.getFields().get(1)));
        when(extractionEngine.extract(anyString(), any(), any(), any(), any()))
                .thenReturn(ExtractionResult.fail("LLM 调用超时", null));

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "x", null);

        assertFalse(resp.isCompleted());
        assertEquals("LLM 调用超时", resp.getMessage());
        verify(validationEngine, never()).validate(any(), any(), any(), any());
        verify(stateManager).save(state);
    }

    @Test
    @DisplayName("无待收集字段且任务完成：跳过提取直接执行动作")
    void noPendingFieldsAndCompleteExecutesAction() {
        TaskState state = midConversationState();
        // receiver already collected too
        state.putField("receiver", FieldState.builder()
                .field("receiver").status(FieldStatus.VALID).value("张三")
                .rawValue("张三").data(new HashMap<>()).build());
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of());
        when(completionEngine.completed(config, state)).thenReturn(true);
        when(mappingEngine.assemble(config, state, null)).thenReturn(Map.of());
        when(actionEngine.execute(eq(config), eq(state), any(), isNull()))
                .thenReturn(ActionResult.success("ok", null));
        when(responseBuilder.buildDone(any())).thenReturn("ok");

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "随便聊聊", null);

        assertTrue(resp.isCompleted());
        verify(extractionEngine, never()).extract(any(), any(), any(), any(), any());
        assertEquals(TaskStatus.COMPLETED, state.getStatus());
    }

    @Test
    @DisplayName("无待收集字段且任务未完成：优雅返回 needMore")
    void noPendingFieldsNotComplete() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state)).thenReturn(List.of());
        when(completionEngine.completed(config, state)).thenReturn(false);
        when(responseBuilder.buildNeedMore(config, state)).thenReturn("请补充收件人");

        AiTaskResponse resp = engine.execute(TASK_TYPE, TASK_ID, "x", null);

        assertFalse(resp.isCompleted());
        assertEquals("请补充收件人", resp.getMessage());
        verify(actionEngine, never()).execute(any(), any(), any(), any());
    }

    // ---------- config version binding ----------

    @Test
    @DisplayName("已有状态绑定版本：使用绑定版本而非最新版本")
    void usesBoundConfigVersion() {
        TaskState state = midConversationState(); // bound to VERSION=3
        when(stateManager.load(TASK_ID, TASK_TYPE, null)).thenReturn(state);
        when(stateManager.load(TASK_ID, TASK_TYPE, VERSION)).thenReturn(state);
        when(configService.get(TASK_TYPE, VERSION)).thenReturn(config);

        when(fieldSelector.select(config, state)).thenReturn(List.of());
        when(completionEngine.completed(config, state)).thenReturn(false);
        when(responseBuilder.buildNeedMore(config, state)).thenReturn("msg");

        engine.execute(TASK_TYPE, TASK_ID, "x", null);

        verify(configService, never()).getLatestVersion(anyString());
        verify(configService).get(TASK_TYPE, VERSION);
    }

    // ---------- exception safety net ----------

    @Test
    @DisplayName("配置缺失：不抛异常，返回优雅错误响应")
    void configMissingDegradesGracefully() {
        // brand-new task with no bound version -> engine resolves latest version
        TaskState fresh = TaskState.builder()
                .taskId(TASK_ID).taskType(TASK_TYPE)
                .status(TaskStatus.INITIALIZED)
                .fields(new HashMap<>())
                .context(new HashMap<>())
                .build();
        when(stateManager.load(TASK_ID, TASK_TYPE, null)).thenReturn(fresh);
        when(configService.getLatestVersion(TASK_TYPE)).thenReturn(VERSION);
        when(configService.get(TASK_TYPE, VERSION))
                .thenThrow(new ConfigNotFoundException(TASK_TYPE, VERSION));

        AiTaskResponse resp = assertDoesNotThrow(
                () -> engine.execute(TASK_TYPE, TASK_ID, "hello", null));

        assertFalse(resp.isCompleted());
        assertNotNull(resp.getMessage());
        assertTrue(resp.getMessage().contains("异常"));
    }

    @Test
    @DisplayName("子引擎抛出运行时异常：状态标记 FAILED 并持久化，不向上抛")
    void unexpectedExceptionMarksFailedAndPersists() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state))
                .thenThrow(new RuntimeException("boom"));

        AiTaskResponse resp = assertDoesNotThrow(
                () -> engine.execute(TASK_TYPE, TASK_ID, "hello", null));

        assertFalse(resp.isCompleted());
        assertEquals(TaskStatus.FAILED, state.getStatus());
        verify(stateManager).save(state);
    }

    @Test
    @DisplayName("异常兜底时保存状态也失败：仍返回错误响应而不抛出")
    void exceptionDuringSaveStillReturnsGracefully() {
        TaskState state = midConversationState();
        stubConfigLoading(state);

        when(fieldSelector.select(config, state))
                .thenThrow(new RuntimeException("boom"));
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(stateManager).save(any());

        AiTaskResponse resp = assertDoesNotThrow(
                () -> engine.execute(TASK_TYPE, TASK_ID, "hello", null));

        assertFalse(resp.isCompleted());
        assertNotNull(resp.getMessage());
    }
}
