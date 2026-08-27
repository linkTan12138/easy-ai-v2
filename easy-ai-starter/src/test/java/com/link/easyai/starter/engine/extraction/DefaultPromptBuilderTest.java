package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.config.OptionDefinition;
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

/**
 * Tests for {@link DefaultPromptBuilder}.
 */
class DefaultPromptBuilderTest {

    private DefaultPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DefaultPromptBuilder();
    }

    private FieldDefinition field(String code, String name, FieldType type,
                                  String description, List<String> examples) {
        FieldDefinition.FieldDefinitionBuilder f = FieldDefinition.builder()
                .code(code)
                .name(name)
                .type(type);
        if (description != null || examples != null) {
            f.extraction(ExtractionConfig.builder()
                    .description(description)
                    .examples(examples)
                    .build());
        }
        return f.build();
    }

    @Test
    @DisplayName("prompt 包含字段代码、名称、说明和示例")
    void promptContainsFieldBasics() {
        String prompt = builder.build(
                List.of(field("waybillNo", "运单号", FieldType.STRING, "国际运单号", List.of("SF123456789"))),
                null, null);

        assertTrue(prompt.contains("waybillNo"));
        assertTrue(prompt.contains("运单号"));
        assertTrue(prompt.contains("国际运单号"));
        assertTrue(prompt.contains("SF123456789"));
        assertTrue(prompt.contains("STRING"));
    }

    @Test
    @DisplayName("prompt 包含枚举可选值")
    void promptContainsOptions() {
        FieldDefinition f = field("channel", "渠道", FieldType.STRING, null, null);
        f.setOptions(List.of(
                OptionDefinition.builder().label("DHL").value(1).build(),
                OptionDefinition.builder().label("FedEx").value(2).build()));

        String prompt = builder.build(List.of(f), null, null);

        assertTrue(prompt.contains("DHL"));
        assertTrue(prompt.contains("FedEx"));
    }

    @Test
    @DisplayName("prompt 包含 JSON 输出契约")
    void promptContainsOutputContract() {
        String prompt = builder.build(
                List.of(field("a", "A", FieldType.STRING, null, null)), null, null);

        assertTrue(prompt.contains("\"fields\""));
        assertTrue(prompt.contains("JSON"));
    }

    @Test
    @DisplayName("已收集的完成字段出现在上下文摘要中")
    void collectedFieldsAppearInSummary() {
        Map<String, FieldState> fields = new HashMap<>();
        fields.put("country", FieldState.builder()
                .field("country").status(FieldStatus.VALID).value("US").build());
        fields.put("pending1", FieldState.builder()
                .field("pending1").status(FieldStatus.PENDING).build());

        TaskState state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();

        String prompt = builder.build(
                List.of(field("channel", "渠道", FieldType.STRING, null, null)), null, state);

        assertTrue(prompt.contains("country=US"));
        assertFalse(prompt.contains("pending1="));
    }

    @Test
    @DisplayName("allowEmpty 字段被标注")
    void allowEmptyFieldsAnnotated() {
        FieldDefinition f = FieldDefinition.builder()
                .code("remark").name("备注").type(FieldType.STRING)
                .extraction(ExtractionConfig.builder().allowEmpty(true).build())
                .build();

        String prompt = builder.build(List.of(f), null, null);

        assertTrue(prompt.contains("remark"));
        assertTrue(prompt.contains("允许为空"));
    }

    @Test
    @DisplayName("空 state 不产生上下文摘要行")
    void emptyStateProducesNoSummary() {
        TaskState state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(new HashMap<>()).build();

        String prompt = builder.build(
                List.of(field("a", "A", FieldType.STRING, null, null)), null, state);

        assertFalse(prompt.contains("已收集字段"));
    }

    @Test
    @DisplayName("存在已收集字段时 prompt 包含更正规则（重新提供则覆盖旧值）")
    void correctionRuleAppearsWhenCollectedFieldsExist() {
        Map<String, FieldState> fields = new HashMap<>();
        fields.put("waybillNo", FieldState.builder()
                .field("waybillNo").status(FieldStatus.VALID).value("JT234222").build());

        TaskState state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();

        String prompt = builder.build(
                List.of(field("channel", "渠道", FieldType.STRING, null, null)), null, state);

        assertTrue(prompt.contains("更正"));
        assertTrue(prompt.contains("覆盖旧值"));
        // 不再出现“不要重复输出”这类压制更正的措辞
        assertFalse(prompt.contains("不要重复输出"));
    }

    @Test
    @DisplayName("传入全量字段定义时，已收集摘要携带字段名称与说明（用户可用别名/含义指代更正）")
    void collectedSummaryIncludesFieldSemantics() {
        Map<String, FieldState> fields = new HashMap<>();
        fields.put("customerNos", FieldState.builder()
                .field("customerNos").status(FieldStatus.VALID).value("test1123").build());
        TaskState state = TaskState.builder()
                .taskId("1").taskType("T").configVersion(1)
                .fields(fields).build();

        FieldDefinition customerNos = field("customerNos", "客户单号", FieldType.STRING_LIST,
                "需要修改订单的客户单号", List.of("test1123"));
        String prompt = builder.build(
                List.of(field("channel", "渠道", FieldType.STRING, null, null)),
                List.of(customerNos),
                state);

        // 摘要行带字段代码、名称和说明，帮助模型把“订单test1223”关联到 customerNos
        assertTrue(prompt.contains("customerNos（客户单号"));
        assertTrue(prompt.contains("说明: 需要修改订单的客户单号"));
        assertTrue(prompt.contains("）=test1123"));
        // 更正规则明确允许用户以名称/别名/含义指代已收集字段
        assertTrue(prompt.contains("别名"));
        assertTrue(prompt.contains("指代该字段"));
    }

    @Test
    @DisplayName("抽取规则 extraction.rules 出现在 prompt 中")
    void extractionRulesAppear() {
        FieldDefinition f = FieldDefinition.builder()
                .code("phone").name("电话").type(FieldType.STRING)
                .extraction(ExtractionConfig.builder()
                        .rules(List.of("必须是11位手机号"))
                        .build())
                .build();

        String prompt = builder.build(List.of(f), null, null);

        assertTrue(prompt.contains("必须是11位手机号"));
    }
}
