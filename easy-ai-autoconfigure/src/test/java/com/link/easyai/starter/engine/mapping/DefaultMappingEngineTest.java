package com.link.easyai.starter.engine.mapping;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.config.MappingRule;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultMappingEngine} and {@link DefaultFieldAssembler}.
 */
class DefaultMappingEngineTest {

    private DefaultMappingEngine engine;
    private AiTaskConfig config;

    @BeforeEach
    void setUp() {
        engine = new DefaultMappingEngine(new DefaultFieldAssembler());

        FieldDefinition channel = FieldDefinition.builder()
                .code("channel").name("渠道").type(FieldType.STRING)
                .mappings(java.util.List.of(
                        MappingRule.builder().target("info.receiveChannelId").source("$data.id").build(),
                        MappingRule.builder().target("info.receiveChannelName").source("$value").build(),
                        MappingRule.builder().target("info.channelRaw").source("$rawValue").build()))
                .build();
        FieldDefinition country = FieldDefinition.builder()
                .code("country").name("目的国").type(FieldType.STRING)
                .mappings(java.util.List.of(
                        MappingRule.builder().target("info.countryCode").build())) // no source -> $value
                .build();
        FieldDefinition pending = FieldDefinition.builder()
                .code("weight").name("重量").type(FieldType.DECIMAL)
                .mappings(java.util.List.of(
                        MappingRule.builder().target("info.weight").source("$value").build()))
                .build();
        FieldDefinition constant = FieldDefinition.builder()
                .code("source").name("来源").type(FieldType.STRING)
                .mappings(java.util.List.of(
                        MappingRule.builder().target("info.source").source("AI_CHAT").build()))
                .build();

        config = AiTaskConfig.builder()
                .taskType("T").version(1)
                .fields(java.util.List.of(channel, country, pending, constant))
                .build();
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
    @DisplayName("$value / $rawValue / $data.xxx 表达式全部正确解析")
    void sourceExpressionsResolved() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", 123L);
        data.put("channelName", "DHL");

        TaskState state = stateWith(FieldState.builder()
                .field("channel").status(FieldStatus.VALID)
                .rawValue("DHL快递").value("DHL").data(data).build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertEquals(123L, params.get("info.receiveChannelId"));
        assertEquals("DHL", params.get("info.receiveChannelName"));
        assertEquals("DHL快递", params.get("info.channelRaw"));
    }

    @Test
    @DisplayName("无 source 的规则默认取 $value")
    void missingSourceDefaultsToValue() {
        TaskState state = stateWith(FieldState.builder()
                .field("country").status(FieldStatus.VALID)
                .rawValue("美国").value("US").build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertEquals("US", params.get("info.countryCode"));
    }

    @Test
    @DisplayName("未完成字段（PENDING/INVALID）不参与映射")
    void incompleteFieldsSkipped() {
        TaskState state = stateWith(
                FieldState.builder().field("weight").status(FieldStatus.PENDING).build(),
                FieldState.builder().field("channel").status(FieldStatus.VALID)
                        .rawValue("DHL").value("DHL").build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertFalse(params.containsKey("info.weight"));
        assertTrue(params.containsKey("info.receiveChannelName"));
    }

    @Test
    @DisplayName("字面量常量直接作为值映射")
    void literalConstantMapped() {
        TaskState state = stateWith(FieldState.builder()
                .field("source").status(FieldStatus.VALID)
                .rawValue("x").value("x").build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertEquals("AI_CHAT", params.get("info.source"));
    }

    @Test
    @DisplayName("$data.key 不存在时该规则被跳过")
    void unresolvedDataKeySkipped() {
        TaskState state = stateWith(FieldState.builder()
                .field("channel").status(FieldStatus.VALID)
                .rawValue("DHL").value("DHL")
                .data(new HashMap<>()).build()); // no id key

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertFalse(params.containsKey("info.receiveChannelId"));
        assertTrue(params.containsKey("info.receiveChannelName"));
    }

    @Test
    @DisplayName("SKIPPED 且无值的字段不产生映射")
    void skippedFieldWithoutValueNotMapped() {
        TaskState state = stateWith(FieldState.builder()
                .field("channel").status(FieldStatus.SKIPPED).build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertTrue(params.isEmpty());
    }

    @Test
    @DisplayName("CONFIRMED 字段正常映射")
    void confirmedFieldMapped() {
        TaskState state = stateWith(FieldState.builder()
                .field("country").status(FieldStatus.CONFIRMED)
                .rawValue("美国").value("US").build());

        Map<String, Object> params = engine.assemble(config, state, new TaskContext());

        assertEquals("US", params.get("info.countryCode"));
    }

    @Test
    @DisplayName("空 state / 空 config 返回空 map")
    void emptyInputsReturnEmptyMap() {
        assertTrue(engine.assemble(null, null, null).isEmpty());
        assertTrue(engine.assemble(config,
                TaskState.builder().fields(new HashMap<>()).build(), null).isEmpty());
    }

    // ---------- DefaultFieldAssembler direct tests ----------

    @Test
    @DisplayName("FieldAssembler: 未知 $ 表达式被跳过")
    void assemblerSkipsUnknownExpression() {
        DefaultFieldAssembler assembler = new DefaultFieldAssembler();
        FieldDefinition def = FieldDefinition.builder()
                .code("x").name("X").type(FieldType.STRING)
                .mappings(java.util.List.of(
                        MappingRule.builder().target("t").source("$unknown").build()))
                .build();

        var values = assembler.assemble(def,
                ValidationResult.success("v"), null);

        assertTrue(values.isEmpty());
    }

    @Test
    @DisplayName("FieldAssembler: null 映射规则列表返回空")
    void assemblerNullRulesReturnsEmpty() {
        DefaultFieldAssembler assembler = new DefaultFieldAssembler();
        FieldDefinition def = FieldDefinition.builder()
                .code("x").name("X").type(FieldType.STRING).build();

        assertTrue(assembler.assemble(def, ValidationResult.success("v"), null).isEmpty());
        assertTrue(assembler.assemble(null, ValidationResult.success("v"), null).isEmpty());
    }
}
