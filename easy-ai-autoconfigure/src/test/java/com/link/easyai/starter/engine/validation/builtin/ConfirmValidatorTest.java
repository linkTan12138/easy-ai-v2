package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfirmValidator}.
 */
class ConfirmValidatorTest {

    private ConfirmValidator validator;
    private FieldContext context;

    @BeforeEach
    void setUp() {
        validator = new ConfirmValidator();
        FieldDefinition field = FieldDefinition.builder()
                .code("isConfirm")
                .name("确认信息")
                .options(List.of(
                        OptionDefinition.builder().label("确认").value(1).build(),
                        OptionDefinition.builder().label("其他").value(0).build()))
                .build();
        context = FieldContext.builder()
                .fieldCode("isConfirm")
                .fieldDefinition(field)
                .build();
    }

    @Test
    @DisplayName("用户回复“确认” -> value=1, confirmed=true")
    void confirmLabelConfirmed() {
        ValidationResult result = validator.validate("确认", context, null);

        assertTrue(result.isValid());
        assertEquals(1, result.getValue());
        assertEquals(Boolean.TRUE, result.getData().get("confirmed"));
    }

    @Test
    @DisplayName("用户回复“其他” -> value=0, confirmed=false")
    void otherLabelNotConfirmed() {
        ValidationResult result = validator.validate("其他", context, null);

        assertTrue(result.isValid());
        assertEquals(0, result.getValue());
        assertEquals(Boolean.FALSE, result.getData().get("confirmed"));
    }

    @Test
    @DisplayName("回复无效内容 -> 校验失败")
    void invalidReplyFails() {
        ValidationResult result = validator.validate("随便什么", context, null);

        assertFalse(result.isValid());
        assertEquals(ConfirmValidator.CODE_INVALID, result.getErrorCode());
        assertTrue(result.getMessage().contains("确认"));
    }

    @Test
    @DisplayName("通过 params.confirmValue 自定义确认值")
    void customConfirmValue() {
        // With confirmValue=0, replying "其他" counts as the confirmation value
        Map<String, Object> params = Map.of("confirmValue", 0);

        ValidationResult other = validator.validate("其他", context, params);
        assertTrue(other.isValid());
        assertEquals(Boolean.TRUE, other.getData().get("confirmed"));

        ValidationResult confirm = validator.validate("确认", context, params);
        assertTrue(confirm.isValid());
        assertEquals(Boolean.FALSE, confirm.getData().get("confirmed"));
    }

    @Test
    @DisplayName("字段无 options -> CONFIRM_NO_OPTIONS")
    void noOptionsFails() {
        FieldContext empty = FieldContext.builder()
                .fieldCode("x")
                .fieldDefinition(FieldDefinition.builder().code("x").name("x").build())
                .build();

        ValidationResult result = validator.validate("确认", empty, null);

        assertFalse(result.isValid());
        assertEquals(ConfirmValidator.CODE_NO_OPTIONS, result.getErrorCode());
    }
}
