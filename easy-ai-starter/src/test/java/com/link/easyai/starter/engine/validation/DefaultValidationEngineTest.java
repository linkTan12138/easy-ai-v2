package com.link.easyai.starter.engine.validation;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.config.ValidationConfig;
import com.link.easyai.starter.engine.config.ValidatorDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.extraction.ExtractionResult;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStatus;
import com.link.easyai.starter.engine.validation.builtin.EnumValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultValidationEngine}: pipeline chaining, state updates,
 * error handling, and onFail strategies.
 */
class DefaultValidationEngineTest {

    private ValidatorRegistry registry;
    private DefaultValidationEngine engine;

    @BeforeEach
    void setUp() {
        registry = new ValidatorRegistry();
        registry.register(new EnumValidator());
        engine = new DefaultValidationEngine(registry);
    }

    // ---------- helpers ----------

    private AiTaskConfig config(FieldDefinition... fields) {
        return AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(fields))
                .build();
    }

    private FieldDefinition field(String code, ValidationConfig validation) {
        return FieldDefinition.builder()
                .code(code)
                .name(code)
                .validation(validation)
                .build();
    }

    private ValidationConfig pipeline(ValidatorDefinition... validators) {
        return ValidationConfig.builder()
                .mode("PIPELINE")
                .validators(List.of(validators))
                .build();
    }

    private TaskState state() {
        return TaskState.builder()
                .taskId("task-1")
                .taskType("ORDER_UPDATE")
                .configVersion(1)
                .status(TaskStatus.COLLECTING)
                .fields(new HashMap<>())
                .context(new HashMap<>())
                .build();
    }

    private ExtractionResult extraction(Map<String, Object> fields) {
        return ExtractionResult.builder().success(true).fields(fields).build();
    }

    // ---------- pipeline chaining ----------

    @Test
    @DisplayName("管道链式执行：ENUM 转换后的值传给下一个校验器，data 合并")
    void pipelineChainsAndMergesData() {
        // Second validator receives the ENUM-transformed value and appends data
        FieldValidator appender = new FieldValidator() {
            @Override
            public String type() {
                return "TEST_APPENDER";
            }

            @Override
            public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
                return ValidationResult.success(rawValue, rawValue, Map.of("touched", "yes"));
            }
        };
        registry.register(appender);

        FieldDefinition declareType = FieldDefinition.builder()
                .code("declareType")
                .name("报关方式")
                .options(List.of(OptionDefinition.builder().label("买单报关").value(3).build()))
                .validation(pipeline(
                        ValidatorDefinition.builder().type("ENUM").build(),
                        ValidatorDefinition.builder().type("TEST_APPENDER").build()))
                .build();

        TaskState state = state();
        engine.validate(extraction(Map.of("declareType", "买单报关")),
                config(declareType), state, null);

        FieldState fs = state.getField("declareType");
        assertEquals(FieldStatus.VALID, fs.getStatus());
        assertEquals(3, fs.getValue());
        assertEquals("买单报关", fs.getRawValue());
        assertEquals("yes", fs.getData().get("touched"));
        assertNull(fs.getErrorCode());
    }

    @Test
    @DisplayName("校验失败时字段标记 INVALID 并记录 errorCode/errorMessage")
    void invalidFieldMarkedWithErrorCode() {
        FieldDefinition declareType = FieldDefinition.builder()
                .code("declareType")
                .name("报关方式")
                .options(List.of(OptionDefinition.builder().label("买单报关").value(3).build()))
                .validation(pipeline(ValidatorDefinition.builder().type("ENUM").build()))
                .build();

        TaskState state = state();
        engine.validate(extraction(Map.of("declareType", "乱写的")),
                config(declareType), state, null);

        FieldState fs = state.getField("declareType");
        assertEquals(FieldStatus.INVALID, fs.getStatus());
        assertEquals("乱写的", fs.getRawValue());
        assertNull(fs.getValue());
        assertEquals("ENUM_VALUE_INVALID", fs.getErrorCode());
        assertNotNull(fs.getErrorMessage());
        // Task is not blocked with default onFail=RETRY
        assertEquals(TaskStatus.COLLECTING, state.getStatus());
    }

    @Test
    @DisplayName("未配置 validation 的字段直接 VALID 保留原始值")
    void fieldWithoutValidationConfigPasses() {
        TaskState state = state();
        engine.validate(extraction(Map.of("remark", "随便说说")),
                config(field("remark", null)), state, null);

        FieldState fs = state.getField("remark");
        assertEquals(FieldStatus.VALID, fs.getStatus());
        assertEquals("随便说说", fs.getValue());
    }

    // ---------- robustness ----------

    @Test
    @DisplayName("LLM 返回未知字段时忽略，不产生状态")
    void unknownFieldIgnored() {
        TaskState state = state();
        engine.validate(extraction(Map.of("noSuchField", "x")),
                config(field("realField", null)), state, null);

        assertNull(state.getField("noSuchField"));
        assertNull(state.getField("realField"));
    }

    @Test
    @DisplayName("空值字段跳过校验")
    void emptyValueSkipped() {
        TaskState state = state();
        Map<String, Object> fields = new HashMap<>();
        fields.put("remark", null);
        fields.put("note", "  ");
        fields.put("list", List.of());
        engine.validate(extraction(fields),
                config(field("remark", null), field("note", null), field("list", null)),
                state, null);

        assertTrue(state.getFields().isEmpty());
    }

    @Test
    @DisplayName("配置引用未注册的校验器 -> VALIDATOR_NOT_FOUND")
    void unknownValidatorTypeFails() {
        TaskState state = state();
        engine.validate(extraction(Map.of("f", "v")),
                config(field("f", pipeline(ValidatorDefinition.builder().type("NOPE").build()))),
                state, null);

        FieldState fs = state.getField("f");
        assertEquals(FieldStatus.INVALID, fs.getStatus());
        assertEquals("VALIDATOR_NOT_FOUND", fs.getErrorCode());
    }

    @Test
    @DisplayName("校验器抛异常 -> VALIDATOR_ERROR，不影响其他字段")
    void throwingValidatorFailsGracefully() {
        registry.register(new FieldValidator() {
            @Override
            public String type() {
                return "BOOM";
            }

            @Override
            public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
                throw new IllegalStateException("boom");
            }
        });

        TaskState state = state();
        engine.validate(extraction(Map.of("bad", "v", "good", "ok")),
                config(
                        field("bad", pipeline(ValidatorDefinition.builder().type("BOOM").build())),
                        field("good", null)),
                state, null);

        assertEquals("VALIDATOR_ERROR", state.getField("bad").getErrorCode());
        assertEquals(FieldStatus.VALID, state.getField("good").getStatus());
    }

    // ---------- onFail strategy ----------

    @Test
    @DisplayName("onFail=BLOCK 时任务标记 FAILED")
    void blockStrategyMarksTaskFailed() {
        FieldDefinition blocked = FieldDefinition.builder()
                .code("declareType")
                .name("报关方式")
                .options(List.of(OptionDefinition.builder().label("买单报关").value(3).build()))
                .validation(ValidationConfig.builder()
                        .mode("PIPELINE")
                        .onFail("BLOCK")
                        .validators(List.of(ValidatorDefinition.builder().type("ENUM").build()))
                        .build())
                .build();

        TaskState state = state();
        engine.validate(extraction(Map.of("declareType", "乱写的")),
                config(blocked), state, null);

        assertEquals(TaskStatus.FAILED, state.getStatus());
        assertEquals(FieldStatus.INVALID, state.getField("declareType").getStatus());
    }

    // ---------- state update semantics ----------

    @Test
    @DisplayName("同一字段二次校验时覆盖旧状态且版本递增")
    void revalidationIncrementsVersion() {
        FieldDefinition declareType = FieldDefinition.builder()
                .code("declareType")
                .name("报关方式")
                .options(List.of(OptionDefinition.builder().label("买单报关").value(3).build()))
                .validation(pipeline(ValidatorDefinition.builder().type("ENUM").build()))
                .build();
        AiTaskConfig config = config(declareType);

        TaskState state = state();
        // First turn: invalid
        engine.validate(extraction(Map.of("declareType", "乱写的")), config, state, null);
        FieldState first = state.getField("declareType");
        assertEquals(FieldStatus.INVALID, first.getStatus());

        // Second turn: valid -> status flips, version increments, errors cleared
        engine.validate(extraction(Map.of("declareType", "买单报关")), config, state, null);
        FieldState second = state.getField("declareType");
        assertEquals(FieldStatus.VALID, second.getStatus());
        assertEquals(3, second.getValue());
        assertNull(second.getErrorCode());
        assertTrue(second.getVersion() > first.getVersion());
    }

    @Test
    @DisplayName("TaskContext 中的 tenantId/userDetails 注入 FieldContext")
    void taskContextInjectedIntoFieldContext() {
        Map<String, Object> captured = new HashMap<>();
        registry.register(new FieldValidator() {
            @Override
            public String type() {
                return "CTX_CAPTURE";
            }

            @Override
            public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) {
                captured.put("tenantId", context.get("tenantId"));
                captured.put("userDetails", context.get("userDetails"));
                return ValidationResult.success(rawValue);
            }
        });

        TaskContext taskContext = TaskContext.builder()
                .taskId("task-1")
                .tenantId(66L)
                .userDetails("user-9")
                .data(Map.of("extra", "e1"))
                .build();

        TaskState state = state();
        engine.validate(extraction(Map.of("f", "v")),
                config(field("f", pipeline(ValidatorDefinition.builder().type("CTX_CAPTURE").build()))),
                state, taskContext);

        assertEquals(66L, captured.get("tenantId"));
        assertEquals("user-9", captured.get("userDetails"));
    }

    @Test
    @DisplayName("提取失败或无字段时不做任何处理")
    void failedExtractionIsNoop() {
        TaskState state = state();
        engine.validate(ExtractionResult.fail("llm error", "raw"), config(field("f", null)), state, null);
        engine.validate(extraction(Map.of()), config(field("f", null)), state, null);
        engine.validate(null, config(field("f", null)), state, null);

        assertTrue(state.getFields().isEmpty());
    }
}
