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
 * Tests for {@link EnumValidator}.
 */
class EnumValidatorTest {

    private EnumValidator validator;
    private FieldContext context;

    @BeforeEach
    void setUp() {
        validator = new EnumValidator();
        FieldDefinition field = FieldDefinition.builder()
                .code("declareType")
                .name("报关方式")
                .options(List.of(
                        OptionDefinition.builder().label("其他").value(0).build(),
                        OptionDefinition.builder().label("单独报关").value(1).build(),
                        OptionDefinition.builder().label("买单报关").value(3).build()))
                .build();
        context = FieldContext.builder()
                .fieldCode("declareType")
                .fieldDefinition(field)
                .build();
    }

    @Test
    @DisplayName("label 匹配后转换为标准 value")
    void labelMatchTransformsToValue() {
        ValidationResult result = validator.validate("买单报关", context, null);

        assertTrue(result.isValid());
        assertEquals(3, result.getValue());
        assertEquals("买单报关", result.getRawValue());
    }

    @Test
    @DisplayName("value 匹配同样通过")
    void valueMatchPasses() {
        ValidationResult result = validator.validate("1", context, null);

        assertTrue(result.isValid());
        assertEquals(1, result.getValue());
    }

    @Test
    @DisplayName("匹配时忽略首尾空格")
    void trimsBeforeMatching() {
        ValidationResult result = validator.validate(" 单独报关 ", context, null);

        assertTrue(result.isValid());
        assertEquals(1, result.getValue());
    }

    @Test
    @DisplayName("不在枚举范围内的值校验失败")
    void invalidValueFails() {
        ValidationResult result = validator.validate("未知方式", context, null);

        assertFalse(result.isValid());
        assertEquals(EnumValidator.CODE_NOT_IN_ENUM, result.getErrorCode());
        assertTrue(result.getMessage().contains("未知方式"));
        assertTrue(result.getMessage().contains("买单报关"));
    }

    @Test
    @DisplayName("列表值逐个校验并转换")
    void listValuesEachValidated() {
        ValidationResult result = validator.validate(List.of("买单报关", "其他"), context, null);

        assertTrue(result.isValid());
        assertEquals(List.of(3, 0), result.getValue());
    }

    @Test
    @DisplayName("列表中包含一个非法元素则整体失败")
    void listWithInvalidElementFails() {
        ValidationResult result = validator.validate(List.of("买单报关", "bad"), context, null);

        assertFalse(result.isValid());
        assertEquals(EnumValidator.CODE_NOT_IN_ENUM, result.getErrorCode());
    }

    @Test
    @DisplayName("字段未配置 options 时返回 ENUM_NO_OPTIONS")
    void noOptionsFails() {
        FieldContext emptyContext = FieldContext.builder()
                .fieldCode("x")
                .fieldDefinition(FieldDefinition.builder().code("x").name("x").build())
                .build();

        ValidationResult result = validator.validate("a", emptyContext, null);

        assertFalse(result.isValid());
        assertEquals(EnumValidator.CODE_NO_OPTIONS, result.getErrorCode());
    }

    @Test
    @DisplayName("params 中的 options 优先于字段定义")
    void paramsOptionsOverrideFieldOptions() {
        Map<String, Object> params = Map.of("options", List.of(
                Map.of("label", "是", "value", "Y"),
                Map.of("label", "否", "value", "N")));

        ValidationResult result = validator.validate("是", context, params);

        assertTrue(result.isValid());
        assertEquals("Y", result.getValue());
    }
}
