package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.premise.PremiseEngine;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultResponseBuilder}: need-more message construction,
 * done message, error messages, and edge cases.
 */
class DefaultResponseBuilderTest {

    private DefaultResponseBuilder builder;

    @BeforeEach
    void setUp() {
        // Default: premise always satisfied (existing tests don't care about premise)
        PremiseEngine alwaysTrue = (premise, state) -> true;
        builder = new DefaultResponseBuilder(alwaysTrue);
    }

    // ---------- helpers ----------

    private FieldDefinition field(String code, String name, boolean required) {
        return FieldDefinition.builder()
                .code(code)
                .name(name)
                .required(required)
                .build();
    }

    private AiTaskConfig config(FieldDefinition... fields) {
        return AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(fields))
                .completion(CompletionConfig.builder()
                        .requiredFields(List.of("customerNos"))
                        .build())
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

    // ---------- buildNeedMore ----------

    @Test
    @DisplayName("已收集字段出现在消息中")
    void collectedFieldsShown() {
        AiTaskConfig config = config(
                field("customerNos", "客户单号", true),
                field("declareType", "报关方式", false));

        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos").status(FieldStatus.VALID).value("ABC123").build());

        String msg = builder.buildNeedMore(config, state);

        assertTrue(msg.contains("已收集"));
        assertTrue(msg.contains("客户单号"));
        assertTrue(msg.contains("ABC123"));
    }

    @Test
    @DisplayName("INVALID 字段的错误消息出现在消息中")
    void errorMessagesShown() {
        AiTaskConfig config = config(
                field("customerNos", "客户单号", true));

        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos")
                .status(FieldStatus.INVALID)
                .rawValue("xxx")
                .errorCode("FORMAT_ERROR")
                .errorMessage("格式不正确")
                .build());

        String msg = builder.buildNeedMore(config, state);

        assertTrue(msg.contains("修正"));
        assertTrue(msg.contains("客户单号"));
        assertTrue(msg.contains("格式不正确"));
    }

    @Test
    @DisplayName("未收集的 required 字段出现在 '请提供' 部分")
    void pendingFieldsShown() {
        AiTaskConfig config = config(
                field("customerNos", "客户单号", true),
                field("declareType", "报关方式", false));

        TaskState state = state();
        // Nothing collected

        String msg = builder.buildNeedMore(config, state);

        assertTrue(msg.contains("请提供"));
        assertTrue(msg.contains("客户单号"));
    }

    @Test
    @DisplayName("premise 不满足的 required 字段不出现在 '请提供' 部分")
    void pendingFieldWithUnmetPremiseHidden() {
        // Use the real premise engine so EXISTS semantics are exercised
        PremiseEngine realEngine = new com.link.easyai.starter.engine.premise.DefaultPremiseEngine();
        DefaultResponseBuilder realBuilder = new DefaultResponseBuilder(realEngine);

        FieldDefinition customerNos = field("customerNos", "客户单号", true);
        FieldDefinition isConfirm = FieldDefinition.builder()
                .code("isConfirm")
                .name("确认信息")
                .required(true)
                .premise(PremiseConfig.builder()
                        .field("customerNos")
                        .conditionOperator("exists")
                        .build())
                .build();

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(customerNos, isConfirm))
                .completion(CompletionConfig.builder()
                        .requiredFields(List.of("customerNos", "isConfirm"))
                        .build())
                .build();

        // customerNos not collected → isConfirm premise unmet → only customerNos asked
        TaskState state = state();
        String msg = realBuilder.buildNeedMore(config, state);

        assertTrue(msg.contains("客户单号"));
        assertFalse(msg.contains("确认信息"), "premise 不满足时不应索要确认信息");
    }

    @Test
    @DisplayName("premise 满足后 required 字段出现在 '请提供' 部分")
    void pendingFieldWithMetPremiseShown() {
        PremiseEngine realEngine = new com.link.easyai.starter.engine.premise.DefaultPremiseEngine();
        DefaultResponseBuilder realBuilder = new DefaultResponseBuilder(realEngine);

        FieldDefinition customerNos = field("customerNos", "客户单号", true);
        FieldDefinition isConfirm = FieldDefinition.builder()
                .code("isConfirm")
                .name("确认信息")
                .required(true)
                .premise(PremiseConfig.builder()
                        .field("customerNos")
                        .conditionOperator("exists")
                        .build())
                .build();

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(customerNos, isConfirm))
                .completion(CompletionConfig.builder()
                        .requiredFields(List.of("customerNos", "isConfirm"))
                        .build())
                .build();

        // customerNos collected (VALID) → isConfirm premise met → both asked
        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos").status(FieldStatus.VALID).value("C001").build());

        String msg = realBuilder.buildNeedMore(config, state);

        assertTrue(msg.contains("确认信息"), "premise 满足后应索要确认信息");
    }

    @Test
    @DisplayName("依赖字段 INVALID 时，premise 不满足，后续字段不出现在 '请提供' 部分")
    void pendingFieldWithInvalidDependencyHidden() {
        PremiseEngine realEngine = new com.link.easyai.starter.engine.premise.DefaultPremiseEngine();
        DefaultResponseBuilder realBuilder = new DefaultResponseBuilder(realEngine);

        FieldDefinition customerNos = field("customerNos", "客户单号", true);
        FieldDefinition isConfirm = FieldDefinition.builder()
                .code("isConfirm")
                .name("确认信息")
                .required(true)
                .premise(PremiseConfig.builder()
                        .field("customerNos")
                        .conditionOperator("exists")
                        .build())
                .build();

        AiTaskConfig config = AiTaskConfig.builder()
                .taskType("ORDER_UPDATE")
                .version(1)
                .fields(List.of(customerNos, isConfirm))
                .completion(CompletionConfig.builder()
                        .requiredFields(List.of("customerNos", "isConfirm"))
                        .build())
                .build();

        // customerNos collected but INVALID → premise NOT met → only customerNos asked
        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos")
                .status(FieldStatus.INVALID)
                .rawValue("bad")
                .errorMessage("客户单号不存在")
                .build());

        String msg = realBuilder.buildNeedMore(config, state);

        assertTrue(msg.contains("客户单号"));
        assertFalse(msg.contains("确认信息"), "依赖字段 INVALID 时不应索要确认信息");
    }

    @Test
    @DisplayName("所有部分同时出现时消息完整")
    void fullMessageWithAllSections() {
        AiTaskConfig config = config(
                field("customerNos", "客户单号", true),
                field("channelCode", "渠道代码", true),
                field("remark", "备注", false));

        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos").status(FieldStatus.VALID).value("C001").build());
        state.putField("channelCode", FieldState.builder()
                .field("channelCode")
                .status(FieldStatus.INVALID)
                .rawValue("bad")
                .errorMessage("渠道不存在")
                .build());

        String msg = builder.buildNeedMore(config, state);

        // Should have collected, error, and pending sections
        assertTrue(msg.contains("已收集"));
        assertTrue(msg.contains("C001"));
        assertTrue(msg.contains("渠道代码"));
        assertTrue(msg.contains("渠道不存在"));
        // channelCode is INVALID so it should appear in pending too
    }

    @Test
    @DisplayName("空 config 或空 state 返回默认消息")
    void nullConfigOrState() {
        assertEquals("请继续提供所需参数。", builder.buildNeedMore(null, null));
    }

    @Test
    @DisplayName("无错误无缺失字段时返回默认消息")
    void nothingToSay() {
        AiTaskConfig config = config(field("customerNos", "客户单号", true));
        TaskState state = state();
        state.putField("customerNos", FieldState.builder()
                .field("customerNos").status(FieldStatus.VALID).value("ok").build());

        String msg = builder.buildNeedMore(config, state);

        // Everything is collected, nothing pending, nothing invalid
        assertTrue(msg.contains("已收集"));
        assertFalse(msg.contains("请提供"));
    }

    // ---------- buildDone ----------

    @Test
    @DisplayName("成功 ActionResult 返回其 message")
    void doneWithSuccessMessage() {
        ActionResult result = ActionResult.success("订单已更新", "ORDER-001");
        assertEquals("订单已更新", builder.buildDone(result));
    }

    @Test
    @DisplayName("成功 ActionResult 无 message 返回默认")
    void doneWithSuccessNoMessage() {
        ActionResult result = ActionResult.success(null, "data");
        assertEquals("任务已完成。", builder.buildDone(result));
    }

    @Test
    @DisplayName("失败 ActionResult 返回 errorMessage")
    void doneWithFailure() {
        ActionResult result = ActionResult.fail("ERR_001", "订单更新失败");
        assertEquals("订单更新失败", builder.buildDone(result));
    }

    @Test
    @DisplayName("失败 ActionResult 无 errorMessage 返回 message")
    void doneWithFailureNoErrorMessage() {
        ActionResult result = ActionResult.builder()
                .success(false)
                .message("操作失败")
                .build();
        assertEquals("操作失败", builder.buildDone(result));
    }

    @Test
    @DisplayName("null ActionResult 返回默认消息")
    void doneWithNull() {
        assertEquals("任务已完成。", builder.buildDone(null));
    }

    @Test
    @DisplayName("失败 ActionResult 无任何消息返回默认失败消息")
    void doneWithFailureNoMessages() {
        ActionResult result = ActionResult.builder().success(false).build();
        assertEquals("任务执行失败，请重试。", builder.buildDone(result));
    }
}
