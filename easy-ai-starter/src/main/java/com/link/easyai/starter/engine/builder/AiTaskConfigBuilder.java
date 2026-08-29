package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.annotation.AiExtract;
import com.link.easyai.starter.engine.annotation.AiField;
import com.link.easyai.starter.engine.annotation.AiMapping;
import com.link.easyai.starter.engine.annotation.AiPremise;
import com.link.easyai.starter.engine.annotation.AiTaskParam;
import com.link.easyai.starter.engine.annotation.AiValid;
import com.link.easyai.starter.engine.annotation.Mapping;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.MappingRule;
import com.link.easyai.starter.engine.config.NormalizationConfig;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.config.TaskExecuteConfig;
import com.link.easyai.starter.engine.config.ValidationConfig;
import com.link.easyai.starter.engine.config.ValidatorDefinition;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import com.link.easyai.starter.engine.premise.PremiseExpressionParser;
import com.link.easyai.starter.engine.task.AiTask;
import com.link.easyai.starter.engine.task.TaskExecutor;
import com.link.easyai.starter.engine.validation.FieldValidator;
import com.link.easyai.starter.engine.validation.builtin.EnumValidator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 {@link AiTask} 注解的执行器类和 {@link AiTaskParam} 注解的参数 DTO 类
 * 转换为 {@link AiTaskConfig}。
 * <p>
 * 两阶段构建：
 * <ol>
 *   <li>{@link #buildBaseConfig(Class)} — 从 @AiTask 执行器读取元信息（name/description/triggers/postActions）</li>
 *   <li>{@link #buildFields(Class, AiBeanResolver)} — 从 @AiTaskParam DTO 读取字段定义</li>
 * </ol>
 * 纯动作场景（无参数）只有 @AiTask 执行器，没有 @AiTaskParam DTO，fields 为空。
 */
@Component
public class AiTaskConfigBuilder {

    /** 注解配置在代码中不可变，因此版本恒为 1。 */
    public static final int ANNOTATION_CONFIG_VERSION = 1;

    private static final String SOURCE_VALUE = "$value";
    private static final String SOURCE_RAW_VALUE = "$rawValue";
    private static final String SOURCE_DATA_PREFIX = "$data.";

    /**
     * 从 @AiTask 执行器类构建基础配置（元信息 + executeConfig）。
     *
     * @param executorClass 标注 @AiTask 的 TaskExecutor 实现类
     * @return 基础配置（taskType/version/name/description/keywords/examples/executeConfig），fields 为空
     */
    public AiTaskConfig buildBaseConfig(Class<? extends TaskExecutor> executorClass) {
        AiTask task = executorClass.getAnnotation(AiTask.class);
        if (task == null) {
            throw new ConfigValidationException(
                    "类 " + executorClass.getName() + " 缺少 @AiTask 注解");
        }
        if (executorClass.isInterface() || Modifier.isAbstract(executorClass.getModifiers())) {
            throw new ConfigValidationException(
                    "@AiTask 只能标注在具体类上: " + executorClass.getName());
        }

        List<String> errors = new ArrayList<>();
        if (isBlank(task.value())) {
            errors.add("@AiTask.value 不能为空: " + executorClass.getName());
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(String.format(
                    "@AiTask 配置校验失败: %s (%s): %s",
                    task.value(), executorClass.getName(), String.join("; ", errors)));
        }

        TaskExecuteConfig executeConfig = TaskExecuteConfig.builder()
                .type(task.value())
                .postActions(task.postActions().length > 0 ? List.of(task.postActions()) : null)
                .params(new LinkedHashMap<>())
                .build();

        // triggers 同时作为 keywords 和 examples 供意图识别使用
        List<String> triggers = task.triggers().length > 0 ? List.of(task.triggers()) : null;

        return AiTaskConfig.builder()
                .taskType(task.value())
                .version(ANNOTATION_CONFIG_VERSION)
                .name(task.name().isBlank() ? task.value() : task.name())
                .description(task.description())
                .keywords(triggers)
                .examples(triggers)
                .fields(new ArrayList<>())
                .executeConfig(executeConfig)
                .build();
    }

    /**
     * 从 @AiTaskParam DTO 类构建字段定义列表。
     *
     * @param paramClass 标注 @AiTaskParam 的参数 DTO 类
     * @param resolver   解析校验器 Bean
     * @return 字段定义列表
     */
    public List<FieldDefinition> buildFields(Class<?> paramClass, AiBeanResolver resolver) {
        AiTaskParam param = paramClass.getAnnotation(AiTaskParam.class);
        if (param == null) {
            throw new ConfigValidationException(
                    "类 " + paramClass.getName() + " 缺少 @AiTaskParam 注解");
        }
        if (paramClass.isInterface() || paramClass.isEnum() || Modifier.isAbstract(paramClass.getModifiers())) {
            throw new ConfigValidationException(
                    "@AiTaskParam 只能标注在具体类上: " + paramClass.getName());
        }
        if (isBlank(param.type())) {
            throw new ConfigValidationException(
                    "@AiTaskParam.type 不能为空: " + paramClass.getName());
        }

        List<String> errors = new ArrayList<>();
        List<FieldDefinition> fields = new ArrayList<>();
        Set<String> codes = new HashSet<>();

        for (Field javaField : paramClass.getDeclaredFields()) {
            if (Modifier.isStatic(javaField.getModifiers()) || javaField.isSynthetic()) {
                continue;
            }
            String code = javaField.getName();
            if (!codes.add(code)) {
                errors.add("字段 code 重复: " + code);
                continue;
            }

            FieldDefinition definition = buildFieldDefinition(javaField, fields.size() + 1, resolver, errors);
            fields.add(definition);
        }

        // 跨字段校验：premise 表达式引用
        for (FieldDefinition definition : fields) {
            validatePremise(paramClass, definition, codes, errors);
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(String.format(
                    "@AiTaskParam 配置校验失败: %s (%s): %s",
                    param.type(), paramClass.getName(), String.join("; ", errors)));
        }

        return fields;
    }

    /**
     * 从字段列表构建 CompletionConfig（requiredFields / optionalFields）。
     */
    public CompletionConfig buildCompletion(List<FieldDefinition> fields) {
        List<String> requiredFields = new ArrayList<>();
        List<String> optionalFields = new ArrayList<>();
        for (FieldDefinition field : fields) {
            if (field.isRequired()) {
                requiredFields.add(field.getCode());
            } else {
                optionalFields.add(field.getCode());
            }
        }
        return CompletionConfig.builder()
                .requiredFields(requiredFields)
                .optionalFields(optionalFields)
                .build();
    }

    /**
     * 从 Java 字段 + 注解构建一个 FieldDefinition。
     *
     * @param order 字段在类中的声明顺序（从1开始）
     */
    private FieldDefinition buildFieldDefinition(Field javaField,
                                                 int order,
                                                 AiBeanResolver resolver,
                                                 List<String> errors) {
        String code = javaField.getName();
        AiField aiField = javaField.getAnnotation(AiField.class);

        // ---- code / name / required / sensitive（约定优先）----
        String name = aiField != null && !isBlank(aiField.name()) ? aiField.name().trim() : code;
        boolean required = aiField != null && aiField.required();
        boolean sensitive = aiField != null && aiField.sensitive();

        // ---- type（Java 类型推导，含枚举选项）----
        FieldTypeResolver.Resolution resolution;
        try {
            resolution = FieldTypeResolver.resolve(javaField);
        } catch (ConfigValidationException e) {
            errors.add(e.getMessage());
            resolution = null;
        }

        // ---- extraction ----
        AiExtract extract = javaField.getAnnotation(AiExtract.class);
        ExtractionConfig extraction = null;
        if (extract != null) {
            extraction = ExtractionConfig.builder()
                    .description(extract.description())
                    .examples(List.of(extract.examples()))
                    .rules(List.of(extract.rules()))
                    // 约定：可选字段允许 LLM 输出中缺失
                    .allowEmpty(!required)
                    .build();
        }

        // ---- validation pipeline ----
        List<ValidatorDefinition> validators = new ArrayList<>();
        AiValid[] valids = javaField.getAnnotationsByType(AiValid.class);
        if (valids.length > 0) {
            for (AiValid valid : valids) {
                try {
                    FieldValidator validator = resolver.resolve(valid.by());
                    String type = validator.type();
                    if (isBlank(type)) {
                        errors.add(String.format("校验器 %s 的 type() 返回空 (字段 '%s')",
                                valid.by().getName(), code));
                    } else {
                        validators.add(ValidatorDefinition.builder().type(type).build());
                    }
                } catch (RuntimeException e) {
                    errors.add(String.format("字段 '%s' 的校验器 %s 无法解析 (Bean 不存在?): %s",
                            code, valid.by().getName(), e.getMessage()));
                }
            }
        } else if (resolution != null && resolution.isEnum()) {
            // 约定：枚举字段自动追加 ENUM 校验器
            try {
                FieldValidator enumValidator = resolver.resolve(EnumValidator.class);
                validators.add(ValidatorDefinition.builder().type(enumValidator.type()).build());
            } catch (RuntimeException e) {
                errors.add(String.format("字段 '%s' 是枚举，需要内置 ENUM 校验器，但解析失败: %s",
                        code, e.getMessage()));
            }
        }
        ValidationConfig validation = validators.isEmpty()
                ? null
                : ValidationConfig.builder().validators(validators).build();

        // ---- premise（前提条件表达式）----
        AiPremise premiseAnnotation = javaField.getAnnotation(AiPremise.class);
        PremiseConfig premise = null;
        if (premiseAnnotation != null && !isBlank(premiseAnnotation.value())) {
            try {
                premise = new PremiseExpressionParser(premiseAnnotation.value().trim()).parse();
            } catch (PremiseExpressionParser.PremiseParseException e) {
                errors.add(String.format("字段 '%s' 的 @AiPremise 表达式解析失败: %s", code, e.getMessage()));
            }
        }

        // ---- normalization ----
        NormalizationConfig normalization = aiField != null && !isBlank(aiField.normalize())
                ? NormalizationConfig.builder().type(aiField.normalize().trim()).build()
                : null;

        // ---- mappings ----
        AiMapping aiMapping = javaField.getAnnotation(AiMapping.class);
        List<MappingRule> mappings;
        if (aiMapping != null) {
            mappings = new ArrayList<>();
            for (Mapping mapping : aiMapping.value()) {
                String targetError = validateTarget(mapping.target());
                String sourceError = validateSource(mapping.source());
                if (targetError != null) {
                    errors.add(String.format("字段 '%s' 的 @Mapping target 非法: %s", code, targetError));
                }
                if (sourceError != null) {
                    errors.add(String.format("字段 '%s' 的 @Mapping source 非法: %s", code, sourceError));
                }
                if (targetError == null && sourceError == null) {
                    mappings.add(MappingRule.builder()
                            .target(mapping.target().trim())
                            .source(mapping.source().trim())
                            .build());
                }
            }
            if (aiMapping.value().length == 0) {
                errors.add(String.format("字段 '%s' 的 @AiMapping 没有声明任何规则", code));
                mappings = null;
            }
        } else {
            // 约定：字段值映射到同名 target
            mappings = List.of(MappingRule.builder().target(code).source(SOURCE_VALUE).build());
        }

        return FieldDefinition.builder()
                .code(code)
                .name(name)
                .type(resolution != null ? resolution.type() : null)
                .required(required)
                .extraction(extraction)
                .premise(premise)
                .validation(validation)
                .normalization(normalization)
                .mappings(mappings)
                .options(resolution != null ? resolution.options() : null)
                .order(order)
                .sensitive(sensitive)
                .build();
    }

    /**
     * 从 @AiPremise 表达式中收集所有引用的字段名，用于校验。
     */
    private Set<String> collectPremiseFields(String expression) {
        Set<String> fields = new HashSet<>();
        if (isBlank(expression)) {
            return fields;
        }
        // 简单提取：匹配标识符，排除关键字和 null
        String[] tokens = expression.split("[^a-zA-Z0-9_]+");
        Set<String> keywords = Set.of("AND", "OR", "NOT", "IN", "NULL", "true", "false");
        for (String token : tokens) {
            if (!token.isEmpty() && !keywords.contains(token.toUpperCase()) &&
                    !Character.isDigit(token.charAt(0))) {
                fields.add(token);
            }
        }
        return fields;
    }

    private PremiseConfig existsLeaf(String field) {
        return PremiseConfig.builder().field(field).conditionOperator("exists").build();
    }

    private void validatePremise(Class<?> paramClass,
                                 FieldDefinition definition,
                                 Set<String> codes,
                                 List<String> errors) {
        AiPremise premise = null;
        try {
            Field javaField = paramClass.getDeclaredField(definition.getCode());
            premise = javaField.getAnnotation(AiPremise.class);
        } catch (NoSuchFieldException ignored) {
            // 不可能发生 — definition 就是从这个字段构建的
        }
        if (premise == null || isBlank(premise.value())) {
            return;
        }
        Set<String> referencedFields = collectPremiseFields(premise.value());
        for (String reference : referencedFields) {
            if (reference.equals(definition.getCode())) {
                errors.add(String.format("字段 '%s' 的 @AiPremise 不能引用自己", definition.getCode()));
            } else if (!codes.contains(reference)) {
                errors.add(String.format("字段 '%s' 的 @AiPremise 引用了不存在的字段 '%s'",
                        definition.getCode(), reference));
            }
        }
    }

    private String validateTarget(String target) {
        if (isBlank(target)) {
            return "target 不能为空";
        }
        if (target.chars().anyMatch(Character::isWhitespace)) {
            return "target 不能包含空白字符: " + target;
        }
        return null;
    }

    /**
     * source 必须是：$value / $rawValue / $data.xxx / 字面量常量。
     * 其他 $ 前缀的表达式会被拒绝（几乎肯定是拼写错误）。
     */
    private String validateSource(String source) {
        if (isBlank(source)) {
            return "source 不能为空";
        }
        String expr = source.trim();
        if (SOURCE_VALUE.equals(expr) || SOURCE_RAW_VALUE.equals(expr)) {
            return null;
        }
        if (expr.startsWith(SOURCE_DATA_PREFIX)) {
            String key = expr.substring(SOURCE_DATA_PREFIX.length()).trim();
            if (key.isEmpty()) {
                return "$data. 后面必须跟数据键名";
            }
            if (key.chars().anyMatch(Character::isWhitespace)) {
                return "$data 键名不能包含空白字符: " + expr;
            }
            return null;
        }
        if (expr.startsWith("$")) {
            return "无法识别的表达式: " + expr + "（支持 $value / $rawValue / $data.xxx 或字面量）";
        }
        return null; // 字面量常量
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
