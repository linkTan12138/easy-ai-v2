package com.link.easyai.starter.engine.validation.builtin;

import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the generic built-in validators:
 * NOT_EMPTY, REGEX, STRING_LENGTH, NUMBER_RANGE.
 */
class GenericValidatorsTest {

    private final FieldContext context = FieldContext.builder().fieldCode("f").build();

    // ---------- NOT_EMPTY ----------

    @Test
    @DisplayName("NOT_EMPTY: null / 空串 / 空集合都失败")
    void notEmptyRejectsBlanks() {
        NotEmptyValidator validator = new NotEmptyValidator();

        assertFalse(validator.validate(null, context, null).isValid());
        assertFalse(validator.validate("   ", context, null).isValid());
        assertFalse(validator.validate(List.of(), context, null).isValid());
        assertEquals(NotEmptyValidator.CODE_EMPTY,
                validator.validate(null, context, null).getErrorCode());
    }

    @Test
    @DisplayName("NOT_EMPTY: 非空值通过且值不变")
    void notEmptyAcceptsValue() {
        NotEmptyValidator validator = new NotEmptyValidator();

        ValidationResult result = validator.validate("JT123456", context, null);
        assertTrue(result.isValid());
        assertEquals("JT123456", result.getValue());
    }

    // ---------- REGEX ----------

    @Test
    @DisplayName("REGEX: 匹配通过，不匹配失败")
    void regexMatchesAndRejects() {
        RegexValidator validator = new RegexValidator();
        Map<String, Object> params = Map.of("pattern", "^[A-Z]{2}\\d{6}$");

        assertTrue(validator.validate("JT123456", context, params).isValid());
        assertFalse(validator.validate("abc", context, params).isValid());
        assertEquals(RegexValidator.CODE_MISMATCH,
                validator.validate("abc", context, params).getErrorCode());
    }

    @Test
    @DisplayName("REGEX: 未配置 pattern 时失败")
    void regexWithoutPatternFails() {
        RegexValidator validator = new RegexValidator();

        ValidationResult result = validator.validate("x", context, null);
        assertFalse(result.isValid());
        assertEquals(RegexValidator.CODE_NO_PATTERN, result.getErrorCode());
    }

    @Test
    @DisplayName("REGEX: 列表值逐个校验")
    void regexValidatesListElements() {
        RegexValidator validator = new RegexValidator();
        Map<String, Object> params = Map.of("pattern", "^\\d+$");

        assertTrue(validator.validate(List.of("1", "22"), context, params).isValid());
        assertFalse(validator.validate(List.of("1", "x"), context, params).isValid());
    }

    // ---------- STRING_LENGTH ----------

    @Test
    @DisplayName("STRING_LENGTH: 范围内通过，范围外失败")
    void stringLengthBounds() {
        StringLengthValidator validator = new StringLengthValidator();
        Map<String, Object> params = Map.of("min", 2, "max", 5);

        assertTrue(validator.validate("abc", context, params).isValid());
        ValidationResult fail = validator.validate("abcdef", context, params);
        assertFalse(fail.isValid());
        assertEquals(StringLengthValidator.CODE_OUT_OF_RANGE, fail.getErrorCode());
    }

    // ---------- NUMBER_RANGE ----------

    @Test
    @DisplayName("NUMBER_RANGE: 数字字符串被转换为窄类型数字")
    void numberRangeCoercesString() {
        NumberRangeValidator validator = new NumberRangeValidator();
        Map<String, Object> params = Map.of("min", 0, "max", 100);

        ValidationResult result = validator.validate("3", context, params);
        assertTrue(result.isValid());
        assertEquals(3, result.getValue());
    }

    @Test
    @DisplayName("NUMBER_RANGE: 小数字符串转为 Double")
    void numberRangeCoercesDecimal() {
        NumberRangeValidator validator = new NumberRangeValidator();
        Map<String, Object> params = Map.of("min", 0, "max", 100);

        ValidationResult result = validator.validate("1.5", context, params);
        assertTrue(result.isValid());
        assertEquals(1.5, result.getValue());
    }

    @Test
    @DisplayName("NUMBER_RANGE: 超范围和非数字分别失败")
    void numberRangeRejects() {
        NumberRangeValidator validator = new NumberRangeValidator();
        Map<String, Object> params = Map.of("min", 0, "max", 10);

        ValidationResult outOfRange = validator.validate("99", context, params);
        assertFalse(outOfRange.isValid());
        assertEquals(NumberRangeValidator.CODE_OUT_OF_RANGE, outOfRange.getErrorCode());

        ValidationResult notNumber = validator.validate("abc", context, params);
        assertFalse(notNumber.isValid());
        assertEquals(NumberRangeValidator.CODE_NOT_A_NUMBER, notNumber.getErrorCode());
    }

    @Test
    @DisplayName("NUMBER_RANGE: 列表值逐个校验并转换")
    void numberRangeValidatesList() {
        NumberRangeValidator validator = new NumberRangeValidator();
        Map<String, Object> params = Map.of("min", 0, "max", 100);

        ValidationResult result = validator.validate(List.of("1", "2.5"), context, params);
        assertTrue(result.isValid());
        assertEquals(List.of(1, 2.5), result.getValue());
    }
}
