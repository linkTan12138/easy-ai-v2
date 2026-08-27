package com.link.easyai.starter.engine.completion;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultCompletionEngine}: required field checks, optional
 * field handling, INVALID blocking, and fallback logic.
 */
class DefaultCompletionEngineTest {

    private DefaultCompletionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultCompletionEngine();
    }

    // ---------- helpers ----------

    private FieldDefinition field(String code, boolean required) {
        return FieldDefinition.builder()
                .code(code)
                .name(code)
                .required(required)
                .build();
    }

    private AiTaskConfig config(CompletionConfig completion, FieldDefinition... fields) {
        return AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(fields))
                .completion(completion)
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

    private FieldState valid(String code) {
        return FieldState.builder().field(code).status(FieldStatus.VALID).value("ok").build();
    }

    private FieldState confirmed(String code) {
        return FieldState.builder().field(code).status(FieldStatus.CONFIRMED).value("ok").build();
    }

    private FieldState skipped(String code) {
        return FieldState.builder().field(code).status(FieldStatus.SKIPPED).build();
    }

    private FieldState invalid(String code) {
        return FieldState.builder().field(code).status(FieldStatus.INVALID).errorCode("ERR").build();
    }

    // ---------- required field checks ----------

    @Test
    @DisplayName("所有 required 字段 VALID -> 任务完成")
    void allRequiredValid() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a", "b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        state.putField("b", valid("b"));

        assertTrue(engine.completed(config(completion, field("a", true), field("b", true)), state));
    }

    @Test
    @DisplayName("required 字段 CONFIRMED 也算完成")
    void requiredConfirmedCounts() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .build();
        TaskState state = state();
        state.putField("a", confirmed("a"));

        assertTrue(engine.completed(config(completion, field("a", true)), state));
    }

    @Test
    @DisplayName("缺少一个 required 字段 -> 未完成")
    void missingRequiredField() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a", "b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        // b not present

        assertFalse(engine.completed(config(completion, field("a", true), field("b", true)), state));
    }

    @Test
    @DisplayName("required 字段 INVALID -> 未完成")
    void requiredInvalidBlocksCompletion() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .build();
        TaskState state = state();
        state.putField("a", invalid("a"));

        assertFalse(engine.completed(config(completion, field("a", true)), state));
    }

    @Test
    @DisplayName("FieldDefinition.required=true 但不在 requiredFields 列表中也算必填")
    void requiredFromFieldDefinition() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        // "b" is required=true but not in requiredFields, and not present in state

        assertFalse(engine.completed(config(completion,
                field("a", true), field("b", true)), state));
    }

    // ---------- optional field checks ----------

    @Test
    @DisplayName("optional 字段 SKIPPED 不影响完成")
    void optionalSkippedOK() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .optionalFields(List.of("b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        state.putField("b", skipped("b"));

        assertTrue(engine.completed(config(completion, field("a", true), field("b", false)), state));
    }

    @Test
    @DisplayName("optional 字段 VALID 不影响完成")
    void optionalValidOK() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .optionalFields(List.of("b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        state.putField("b", valid("b"));

        assertTrue(engine.completed(config(completion, field("a", true), field("b", false)), state));
    }

    @Test
    @DisplayName("optional 字段不在 state 中不影响完成")
    void optionalAbsentOK() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .optionalFields(List.of("b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        // b never extracted

        assertTrue(engine.completed(config(completion, field("a", true), field("b", false)), state));
    }

    @Test
    @DisplayName("optional 字段 INVALID -> 阻止完成")
    void optionalInvalidBlocks() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .optionalFields(List.of("b"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        state.putField("b", invalid("b"));

        assertFalse(engine.completed(config(completion, field("a", true), field("b", false)), state));
    }

    @Test
    @DisplayName("state 中任意字段 INVALID 都阻止完成")
    void anyInvalidBlocks() {
        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(List.of("a"))
                .build();
        TaskState state = state();
        state.putField("a", valid("a"));
        state.putField("c", invalid("c")); // not in required/optional, but INVALID

        assertFalse(engine.completed(config(completion, field("a", true)), state));
    }

    // ---------- fallback ----------

    @Test
    @DisplayName("无 CompletionConfig 时，FieldDefinition.required=true 作为 fallback 判断")
    void fallbackNoCompletionConfig() {
        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("TEST")
                .version(1)
                .fields(List.of(field("a", true), field("b", false)))
                .build(); // no completion config

        TaskState state = state();
        state.putField("a", valid("a"));

        assertTrue(engine.completed(config, state));
    }

    @Test
    @DisplayName("无 CompletionConfig 且 required 字段未完成 -> false")
    void fallbackRequiredNotCompleted() {
        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("TEST")
                .version(1)
                .fields(List.of(field("a", true)))
                .build();

        TaskState state = state();
        // a not present

        assertFalse(engine.completed(config, state));
    }

    // ---------- edge cases ----------

    @Test
    @DisplayName("空 config 或空 state -> false")
    void nullConfigOrState() {
        assertFalse(engine.completed(null, state()));
        assertFalse(engine.completed(config(null), null));
    }

    @Test
    @DisplayName("空 fields 列表 + 无 requiredFields -> 完成")
    void emptyFieldsComplete() {
        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("TEST")
                .version(1)
                .fields(List.of())
                .completion(CompletionConfig.builder().requiredFields(List.of()).build())
                .build();
        TaskState state = state();

        assertTrue(engine.completed(config, state));
    }
}
