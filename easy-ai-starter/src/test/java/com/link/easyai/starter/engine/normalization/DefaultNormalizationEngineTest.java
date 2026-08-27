package com.link.easyai.starter.engine.normalization;

import com.link.easyai.starter.engine.AiTaskConfigService;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.config.NormalizationConfig;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultNormalizationEngine}.
 */
class DefaultNormalizationEngineTest {

    private AiTaskConfigService configService;
    private DefaultNormalizationEngine engine;
    private AiTaskConfig config;

    /** Test normalizer: appends "-STD" to the value and attaches data */
    static class SuffixNormalizer implements FieldNormalizer {
        @Override public String type() { return "SUFFIX"; }
        @Override public NormalizationResult normalize(Object value, FieldContext ctx, Map<String, Object> params) {
            String suffix = params != null ? String.valueOf(params.getOrDefault("suffix", "-STD")) : "-STD";
            Map<String, Object> data = new HashMap<>();
            data.put("normalizedBy", "SUFFIX");
            return NormalizationResult.success(String.valueOf(value) + suffix, data);
        }
    }

    /** Test normalizer that always fails */
    static class FailingNormalizer implements FieldNormalizer {
        @Override public String type() { return "FAIL"; }
        @Override public NormalizationResult normalize(Object value, FieldContext ctx, Map<String, Object> params) {
            return NormalizationResult.fail("无法标准化该值");
        }
    }

    /** Test normalizer that throws */
    static class ThrowingNormalizer implements FieldNormalizer {
        @Override public String type() { return "THROW"; }
        @Override public NormalizationResult normalize(Object value, FieldContext ctx, Map<String, Object> params) {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        configService = mock(AiTaskConfigService.class);
        engine = new DefaultNormalizationEngine(
                List.of(new SuffixNormalizer(), new FailingNormalizer(), new ThrowingNormalizer()),
                configService);

        FieldDefinition withNormalization = FieldDefinition.builder()
                .code("cargoDesc").name("货物描述").type(FieldType.STRING)
                .normalization(NormalizationConfig.builder()
                        .type("SUFFIX")
                        .params(Map.of("suffix", "-STD"))
                        .build())
                .build();
        FieldDefinition plain = FieldDefinition.builder()
                .code("country").name("目的国").type(FieldType.STRING)
                .build();
        FieldDefinition failing = FieldDefinition.builder()
                .code("badField").name("坏字段").type(FieldType.STRING)
                .normalization(NormalizationConfig.builder().type("FAIL").build())
                .build();
        FieldDefinition missingType = FieldDefinition.builder()
                .code("noNormalizer").name("未注册标准化器").type(FieldType.STRING)
                .normalization(NormalizationConfig.builder().type("NOT_REGISTERED").build())
                .build();
        FieldDefinition throwing = FieldDefinition.builder()
                .code("throwField").name("异常字段").type(FieldType.STRING)
                .normalization(NormalizationConfig.builder().type("THROW").build())
                .build();

        config = AiTaskConfig.builder()
                .taskType("T").version(1)
                .fields(List.of(withNormalization, plain, failing, missingType, throwing))
                .build();
        when(configService.get("T", 1)).thenReturn(config);
    }

    private TaskState stateWith(FieldState... fieldStates) {
        Map<String, FieldState> fields = new HashMap<>();
        for (FieldState fs : fieldStates) {
            fields.put(fs.getField(), fs);
        }
        return TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();
    }

    @Test
    @DisplayName("VALID 字段标准化成功：value 替换 + data 合并 + 标记位")
    void normalizationSuccess() {
        TaskState state = stateWith(FieldState.builder()
                .field("cargoDesc").status(FieldStatus.VALID)
                .rawValue("PI966").value("PI966")
                .version(1).build());

        engine.normalize(state, new TaskContext());

        FieldState updated = state.getField("cargoDesc");
        assertEquals(FieldStatus.VALID, updated.getStatus());
        assertEquals("PI966-STD", updated.getValue());
        assertEquals("SUFFIX", updated.getData().get("normalizedBy"));
        assertEquals(Boolean.TRUE, updated.getData().get(DefaultNormalizationEngine.NORMALIZED_FLAG));
        assertEquals(2, updated.getVersion());
    }

    @Test
    @DisplayName("标准化失败：字段标记 INVALID 并记录错误")
    void normalizationFailureMarksInvalid() {
        TaskState state = stateWith(FieldState.builder()
                .field("badField").status(FieldStatus.VALID)
                .rawValue("x").value("x").build());

        engine.normalize(state, new TaskContext());

        FieldState updated = state.getField("badField");
        assertEquals(FieldStatus.INVALID, updated.getStatus());
        assertNull(updated.getValue());
        assertEquals("NORMALIZATION_FAILED", updated.getErrorCode());
        assertEquals("无法标准化该值", updated.getErrorMessage());
    }

    @Test
    @DisplayName("标准化器抛异常：字段标记 INVALID 且不向外抛出")
    void normalizerExceptionMarksInvalid() {
        TaskState state = stateWith(FieldState.builder()
                .field("throwField").status(FieldStatus.VALID)
                .rawValue("x").value("x").build());

        assertDoesNotThrow(() -> engine.normalize(state, new TaskContext()));

        FieldState updated = state.getField("throwField");
        assertEquals(FieldStatus.INVALID, updated.getStatus());
        assertEquals("NORMALIZATION_FAILED", updated.getErrorCode());
    }

    @Test
    @DisplayName("未注册的标准化器类型：字段保持原值（优雅降级）")
    void unregisteredNormalizerKeepsValue() {
        TaskState state = stateWith(FieldState.builder()
                .field("noNormalizer").status(FieldStatus.VALID)
                .rawValue("x").value("x").build());

        engine.normalize(state, new TaskContext());

        FieldState updated = state.getField("noNormalizer");
        assertEquals(FieldStatus.VALID, updated.getStatus());
        assertEquals("x", updated.getValue());
    }

    @Test
    @DisplayName("已标准化的字段（有标记位）不会重复标准化")
    void alreadyNormalizedSkipped() {
        Map<String, Object> data = new HashMap<>();
        data.put(DefaultNormalizationEngine.NORMALIZED_FLAG, true);
        TaskState state = stateWith(FieldState.builder()
                .field("cargoDesc").status(FieldStatus.VALID)
                .rawValue("PI966").value("PI966-STD")
                .data(data).version(5).build());

        engine.normalize(state, new TaskContext());

        FieldState updated = state.getField("cargoDesc");
        assertEquals("PI966-STD", updated.getValue()); // unchanged
        assertEquals(5, updated.getVersion());          // version untouched
    }

    @Test
    @DisplayName("PENDING / INVALID 字段不参与标准化")
    void nonValidFieldsSkipped() {
        TaskState state = stateWith(
                FieldState.builder().field("cargoDesc").status(FieldStatus.PENDING).build(),
                FieldState.builder().field("badField").status(FieldStatus.INVALID)
                        .errorCode("E").errorMessage("m").build());

        engine.normalize(state, new TaskContext());

        assertEquals(FieldStatus.PENDING, state.getField("cargoDesc").getStatus());
        assertEquals(FieldStatus.INVALID, state.getField("badField").getStatus());
    }

    @Test
    @DisplayName("无标准化配置的字段保持不变")
    void plainFieldUntouched() {
        TaskState state = stateWith(FieldState.builder()
                .field("country").status(FieldStatus.VALID)
                .rawValue("US").value("US").version(1).build());

        engine.normalize(state, new TaskContext());

        FieldState updated = state.getField("country");
        assertEquals("US", updated.getValue());
        assertEquals(1, updated.getVersion());
    }

    @Test
    @DisplayName("CONFIRMED 字段也会被标准化")
    void confirmedFieldNormalized() {
        TaskState state = stateWith(FieldState.builder()
                .field("cargoDesc").status(FieldStatus.CONFIRMED)
                .rawValue("PI966").value("PI966").build());

        engine.normalize(state, new TaskContext());

        assertEquals("PI966-STD", state.getField("cargoDesc").getValue());
    }

    @Test
    @DisplayName("配置加载失败时静默跳过（不抛异常）")
    void configLoadFailureSkips() {
        when(configService.get("T", 1)).thenThrow(new RuntimeException("db down"));
        TaskState state = stateWith(FieldState.builder()
                .field("cargoDesc").status(FieldStatus.VALID)
                .rawValue("PI966").value("PI966").build());

        assertDoesNotThrow(() -> engine.normalize(state, new TaskContext()));
        assertEquals("PI966", state.getField("cargoDesc").getValue());
    }

    @Test
    @DisplayName("空 state 直接返回")
    void emptyStateNoOp() {
        assertDoesNotThrow(() ->
                engine.normalize(TaskState.builder().fields(new HashMap<>()).build(), new TaskContext()));
        assertDoesNotThrow(() -> engine.normalize(null, new TaskContext()));
    }
}
