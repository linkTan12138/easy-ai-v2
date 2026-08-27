package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;
import com.link.easyai.starter.engine.builder.fixtures.NoAnnotationTask;
import com.link.easyai.starter.engine.builder.fixtures.StaticBeanResolver;
import com.link.easyai.starter.engine.builder.fixtures.broken.BlankTask;
import com.link.easyai.starter.engine.builder.fixtures.broken.BrokenTask;
import com.link.easyai.starter.engine.builder.fixtures.broken.UnsupportedTypeTask;
import com.link.easyai.starter.engine.builder.fixtures.valid.FixtureTaskA;
import com.link.easyai.starter.engine.builder.fixtures.valid.FixtureTaskB;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.FieldType;
import com.link.easyai.starter.engine.config.MappingRule;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.builtin.EnumValidator;
import com.link.easyai.starter.engine.validation.builtin.NotEmptyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiTaskConfigBuilder}: convention derivations (code / type /
 * order / options / allowEmpty / default mapping), annotation overrides, and
 * full-picture error collection.
 */
class AiTaskConfigBuilderTest {

    private final AiTaskConfigBuilder builder = new AiTaskConfigBuilder();

    /** Resolver that knows exactly the beans the valid fixtures need. */
    private AiBeanResolver validResolver() {
        return new StaticBeanResolver(
                new FixtureAction(),
                new NotEmptyValidator(),
                new EnumValidator());
    }

    // ---------- happy path: convention over configuration ----------

    @Test
    @DisplayName("完整 fixture：任务元数据、action、postActions 全部生成")
    void buildsTaskMetadata() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        assertEquals("FIXTURE_TASK_A", config.getTaskType());
        assertEquals(1, config.getVersion());
        assertEquals("任务甲", config.getName());
        assertEquals("丰富特性 fixture", config.getDescription());
        assertEquals("FIXTURE_ACTION", config.getAction().getType());
        assertEquals(List.of("LOG", "ECHO"), config.getAction().getPostActions());
    }

    @Test
    @DisplayName("字段声明顺序 → order 1..n；code 取字段名；name 取 @AiField")
    void derivesOrderAndCodes() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        List<String> codes = config.getFields().stream()
                .map(FieldDefinition::getCode).collect(Collectors.toList());
        assertEquals(List.of("customerName", "priority", "remarks", "status", "finalRemark"), codes);

        List<Integer> orders = config.getFields().stream()
                .map(FieldDefinition::getOrder).collect(Collectors.toList());
        assertEquals(List.of(1, 2, 3, 4, 5), orders);

        assertEquals("客户名", config.getField("customerName").getName());
        assertEquals("优先级", config.getField("priority").getName());
        assertEquals("finalRemark", config.getField("finalRemark").getName()); // 无 @AiField → 回退字段名
    }

    @Test
    @DisplayName("Java 类型自动推导：String/Integer/List<String>，枚举带 options")
    void derivesFieldTypes() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        assertEquals(FieldType.STRING, config.getField("customerName").getType());
        assertEquals(FieldType.INTEGER, config.getField("priority").getType());
        assertEquals(FieldType.STRING_LIST, config.getField("remarks").getType());
        assertEquals(FieldType.STRING, config.getField("status").getType());
        assertEquals(FieldType.STRING, config.getField("finalRemark").getType());
    }

    @Test
    @DisplayName("Integer 枚举 → INTEGER + options（声明顺序）")
    void integerEnumGeneratesOptions() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());
        List<OptionDefinition> options = config.getField("priority").getOptions();

        assertNotNull(options);
        assertEquals(2, options.size());
        assertEquals("低", options.get(0).getLabel());
        assertEquals(1, options.get(0).getValue());
        assertEquals("高", options.get(1).getLabel());
        assertEquals(9, options.get(1).getValue());
    }

    @Test
    @DisplayName("String 枚举 → STRING + options")
    void stringEnumGeneratesOptions() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());
        List<OptionDefinition> options = config.getField("status").getOptions();

        assertEquals(2, options.size());
        assertEquals("激活", options.get(0).getLabel());
        assertEquals("A", options.get(0).getValue());
        assertEquals("停用", options.get(1).getLabel());
        assertEquals("I", options.get(1).getValue());
    }

    @Test
    @DisplayName("枚举字段无显式 @AiValid 时自动追加 ENUM 校验器")
    void enumFieldGetsAutoEnumValidator() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());
        List<String> types = validatorTypes(config.getField("priority"));

        assertEquals(List.of("ENUM"), types);
        assertEquals(List.of("ENUM"), validatorTypes(config.getField("status")));
    }

    @Test
    @DisplayName("显式 @AiValid 生成校验管道；allowEmpty = !required")
    void buildsValidationAndExtraction() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());
        FieldDefinition customerName = config.getField("customerName");

        assertEquals(List.of("NOT_EMPTY"), validatorTypes(customerName));
        assertTrue(customerName.isRequired());
        assertTrue(customerName.isSensitive());

        assertNotNull(customerName.getExtraction());
        assertEquals("客户名描述", customerName.getExtraction().getDescription());
        assertEquals(List.of("张三", "李四"), customerName.getExtraction().getExamples());
        assertEquals(List.of("规则一", "规则二"), customerName.getExtraction().getRules());
        assertFalse(customerName.getExtraction().isAllowEmpty()); // required → not allowEmpty

        FieldDefinition remarks = config.getField("remarks");
        assertFalse(remarks.isRequired());
        assertNull(remarks.getExtraction()); // no @AiExtract → no extraction config
        assertNull(remarks.getValidation()); // no validators at all
    }

    @Test
    @DisplayName("@AiField.normalize 生成 NormalizationConfig")
    void buildsNormalization() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        assertEquals("TEST_NORMALIZE", config.getField("remarks").getNormalization().getType());
        assertNull(config.getField("customerName").getNormalization());
    }

    @Test
    @DisplayName("缺省映射：同名 target ← $value；显式 @AiMapping 完全覆盖")
    void buildsMappings() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        List<MappingRule> defaults = config.getField("customerName").getMappings();
        assertEquals(1, defaults.size());
        assertEquals("customerName", defaults.get(0).getTarget());
        assertEquals("$value", defaults.get(0).getSource());

        List<MappingRule> explicit = config.getField("finalRemark").getMappings();
        assertEquals(3, explicit.size());
        assertEquals("finalRemark", explicit.get(0).getTarget());
        assertEquals("$value", explicit.get(0).getSource());
        assertEquals("rawRemark", explicit.get(1).getTarget());
        assertEquals("$rawValue", explicit.get(1).getSource());
        assertEquals("tag", explicit.get(2).getTarget());
        assertEquals("LITERAL_TAG", explicit.get(2).getSource());
    }

    @Test
    @DisplayName("多依赖 @AiDependsOn → AND 组（两个 exists 叶子）")
    void buildsAndPremise() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());
        PremiseConfig premise = config.getField("finalRemark").getPremise();

        assertNotNull(premise);
        assertEquals("AND", premise.getOperator());
        assertEquals(2, premise.getConditions().size());
        assertEquals("customerName", premise.getConditions().get(0).getField());
        assertEquals("exists", premise.getConditions().get(0).getConditionOperator());
        assertEquals("priority", premise.getConditions().get(1).getField());
        assertEquals("exists", premise.getConditions().get(1).getConditionOperator());
    }

    @Test
    @DisplayName("completion: required/optional 字段集合")
    void buildsCompletion() {
        AiTaskConfig config = builder.build(FixtureTaskA.class, validResolver());

        assertEquals(List.of("customerName"), config.getCompletion().getRequiredFields());
        assertEquals(List.of("priority", "remarks", "status", "finalRemark"),
                config.getCompletion().getOptionalFields());
    }

    @Test
    @DisplayName("最小 fixture：全部走约定，action 无 postActions")
    void buildsMinimalTask() {
        AiTaskConfig config = builder.build(FixtureTaskB.class, validResolver());

        assertEquals("FIXTURE_TASK_B", config.getTaskType());
        assertEquals(1, config.getFields().size());
        assertEquals(FieldType.STRING, config.getField("only").getType());
        assertNull(config.getAction().getPostActions());
        assertEquals("FIXTURE_ACTION", config.getAction().getType());
    }

    // ---------- error collection (all-or-nothing, fail-fast) ----------

    @Test
    @DisplayName("损坏 fixture：一次抛出全部四类错误")
    void collectsAllErrors() {
        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> builder.build(BrokenTask.class, validResolver()));

        String message = e.getMessage();
        assertTrue(message.contains("UnregisteredValidator"), "应报告未注册校验器: " + message);
        assertTrue(message.contains("noSuchField"), "应报告悬空依赖: " + message);
        assertTrue(message.contains("$wrong"), "应报告非法映射表达式: " + message);
        assertTrue(message.contains("不能依赖自己"), "应报告自依赖: " + message);
    }

    @Test
    @DisplayName("不支持的 Java 类型：raw List / List<枚举> / 数组 / Map")
    void rejectsUnsupportedTypes() {
        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> builder.build(UnsupportedTypeTask.class, validResolver()));

        String message = e.getMessage();
        assertTrue(message.contains("rawList"), message);
        assertTrue(message.contains("enumList"), message);
        assertTrue(message.contains("names"), message);
        assertTrue(message.contains("attributes"), message);
    }

    @Test
    @DisplayName("@AiTask.type / name 为空时报错")
    void rejectsBlankMetadata() {
        ConfigValidationException e = assertThrows(ConfigValidationException.class,
                () -> builder.build(BlankTask.class, validResolver()));

        assertTrue(e.getMessage().contains("type 不能为空"), e.getMessage());
        assertTrue(e.getMessage().contains("name 不能为空"), e.getMessage());
    }

    @Test
    @DisplayName("缺少 @AiTask 注解直接拒绝")
    void rejectsMissingAnnotation() {
        assertThrows(ConfigValidationException.class,
                () -> builder.build(NoAnnotationTask.class, validResolver()));
    }

    // ---------- helpers ----------

    private List<String> validatorTypes(FieldDefinition field) {
        assertNotNull(field.getValidation(), "validation config expected");
        return field.getValidation().getValidators().stream()
                .map(v -> v.getType())
                .collect(Collectors.toList());
    }
}
