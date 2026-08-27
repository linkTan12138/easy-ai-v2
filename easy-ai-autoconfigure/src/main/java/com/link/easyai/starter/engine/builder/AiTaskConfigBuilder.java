package com.link.easyai.starter.engine.builder;

import com.link.easyai.starter.engine.action.ActionExecutor;
import com.link.easyai.starter.engine.annotation.AiDependsOn;
import com.link.easyai.starter.engine.annotation.AiExtract;
import com.link.easyai.starter.engine.annotation.AiField;
import com.link.easyai.starter.engine.annotation.AiMapping;
import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.annotation.AiValid;
import com.link.easyai.starter.engine.annotation.Mapping;
import com.link.easyai.starter.engine.config.ActionConfig;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.MappingRule;
import com.link.easyai.starter.engine.config.NormalizationConfig;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.config.ValidationConfig;
import com.link.easyai.starter.engine.config.ValidatorDefinition;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
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
 * Converts an {@link AiTask}-annotated DTO class into an
 * {@link AiTaskConfig} that the existing {@link com.link.easyai.starter.engine.AiTaskEngine}
 * consumes unchanged.
 * <p>
 * Convention over configuration — everything derivable from the Java class is
 * derived; annotations only override defaults:
 * <pre>
 * Java Field                 → code / type / order / options(枚举)
 * &#64;AiField                 → name / required / sensitive / normalize
 * &#64;AiExtract               → extraction (allowEmpty = !required)
 * &#64;AiValid                 → validation pipeline (枚举字段缺省自动追加 ENUM 校验器)
 * &#64;AiMapping               → mappings (缺省: 同名 target ← $value)
 * &#64;AiDependsOn             → premise (单依赖: exists 叶子; 多依赖: AND)
 * required 字段集合           → completion.requiredFields / optionalFields
 * &#64;AiTask.action            → action.type (Spring Bean 的 type())
 * </pre>
 * All structural problems (missing bean, bad mapping source, dangling
 * dependsOn reference, unsupported Java type, ...) are collected and reported
 * together as a single {@link ConfigValidationException} so a migration fails
 * fast at startup with the full picture.
 */
@Component
public class AiTaskConfigBuilder {

    /** Annotation configs are immutable in code, so they are always version 1. */
    public static final int ANNOTATION_CONFIG_VERSION = 1;

    private static final String SOURCE_VALUE = "$value";
    private static final String SOURCE_RAW_VALUE = "$rawValue";
    private static final String SOURCE_DATA_PREFIX = "$data.";

    /**
     * Build the config for one @AiTask class.
     *
     * @param taskClass the DTO class annotated with @AiTask
     * @param resolver  resolves validator / action classes into Spring beans
     * @return the built config (taskType + version + fields + completion + action)
     * @throws ConfigValidationException if the declaration is invalid
     */
    public AiTaskConfig build(Class<?> taskClass, AiBeanResolver resolver) {
        AiTask task = taskClass.getAnnotation(AiTask.class);
        if (task == null) {
            throw new ConfigValidationException(
                    "类 " + taskClass.getName() + " 缺少 @AiTask 注解");
        }
        if (taskClass.isInterface() || taskClass.isEnum() || Modifier.isAbstract(taskClass.getModifiers())) {
            throw new ConfigValidationException(
                    "@AiTask 只能标注在具体类上: " + taskClass.getName());
        }

        List<String> errors = new ArrayList<>();

        if (isBlank(task.type())) {
            errors.add("@AiTask.type 不能为空: " + taskClass.getName());
        }
        if (isBlank(task.name())) {
            errors.add("@AiTask.name 不能为空: " + taskClass.getName());
        }

        // Action: resolve the bean and read its type identifier
        String actionType = null;
        try {
            ActionExecutor executor = resolver.resolve(task.action());
            actionType = executor.type();
            if (isBlank(actionType)) {
                errors.add(String.format("@AiTask.action %s 的 type() 返回空: %s",
                        task.action().getName(), taskClass.getName()));
            }
        } catch (RuntimeException e) {
            errors.add(String.format("@AiTask.action %s 无法解析 (Bean 不存在?): %s",
                    task.action().getName(), e.getMessage()));
        }

        // Fields
        List<FieldDefinition> fields = new ArrayList<>();
        List<String> requiredFields = new ArrayList<>();
        List<String> optionalFields = new ArrayList<>();
        Set<String> codes = new HashSet<>();

        for (Field javaField : taskClass.getDeclaredFields()) {
            if (Modifier.isStatic(javaField.getModifiers()) || javaField.isSynthetic()) {
                continue;
            }
            String code = javaField.getName();
            if (!codes.add(code)) {
                errors.add("字段 code 重复: " + code);
                continue;
            }

            // Convention: declaration order → field order (1-based)
            FieldDefinition definition = buildFieldDefinition(task, javaField, fields.size() + 1, resolver, errors);
            fields.add(definition);
            if (definition.isRequired()) {
                requiredFields.add(code);
            } else {
                optionalFields.add(code);
            }
        }

        if (fields.isEmpty()) {
            errors.add("@AiTask 类没有任何实例字段: " + taskClass.getName());
        }

        // Cross-field validation: dependsOn references
        for (FieldDefinition definition : fields) {
            validateDependsOn(taskClass, definition, codes, errors);
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException(String.format(
                    "@AiTask 配置校验失败: %s (%s): %s",
                    task.type(), taskClass.getName(), String.join("; ", errors)));
        }

        CompletionConfig completion = CompletionConfig.builder()
                .requiredFields(requiredFields)
                .optionalFields(optionalFields)
                .build();

        ActionConfig action = ActionConfig.builder()
                .type(actionType)
                .postActions(task.postActions().length > 0 ? List.of(task.postActions()) : null)
                .params(new LinkedHashMap<>())
                .build();

        return AiTaskConfig.builder()
                .taskType(task.type())
                .version(ANNOTATION_CONFIG_VERSION)
                .name(task.name())
                .description(task.description())
                .keywords(task.keywords().length > 0 ? List.of(task.keywords()) : null)
                .examples(task.examples().length > 0 ? List.of(task.examples()) : null)
                .fields(fields)
                .completion(completion)
                .action(action)
                .build();
    }

    /**
     * Build one FieldDefinition from a Java field + its annotations.
     *
     * @param order 1-based declaration order within the task class
     */
    private FieldDefinition buildFieldDefinition(AiTask task,
                                                 Field javaField,
                                                 int order,
                                                 AiBeanResolver resolver,
                                                 List<String> errors) {
        String code = javaField.getName();
        AiField aiField = javaField.getAnnotation(AiField.class);

        // ---- code / name / required / sensitive (convention first) ----
        String name = aiField != null && !isBlank(aiField.name()) ? aiField.name().trim() : code;
        boolean required = aiField != null && aiField.required();
        boolean sensitive = aiField != null && aiField.sensitive();

        // ---- type (Java type derivation, incl. enum options) ----
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
                    // Convention: an optional field may be absent from the LLM output
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
            // Convention: enum fields automatically get the ENUM validator
            // (label -> value conversion against the generated options);
            // an explicit @AiValid overrides this default entirely.
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

        // ---- premise (simple dependency) ----
        AiDependsOn dependsOn = javaField.getAnnotation(AiDependsOn.class);
        PremiseConfig premise = buildPremise(dependsOn);

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
            // Convention: map the field value to the same-named target
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
     * Build the premise config from @AiDependsOn:
     * single dependency → a bare "exists" leaf (identical to legacy DB JSON);
     * multiple dependencies → an AND group of "exists" leaves.
     */
    private PremiseConfig buildPremise(AiDependsOn dependsOn) {
        if (dependsOn == null || dependsOn.value().length == 0) {
            return null;
        }
        if (dependsOn.value().length == 1) {
            return existsLeaf(dependsOn.value()[0]);
        }
        List<PremiseConfig> conditions = new ArrayList<>(dependsOn.value().length);
        for (String field : dependsOn.value()) {
            conditions.add(existsLeaf(field));
        }
        return PremiseConfig.builder().operator("AND").conditions(conditions).build();
    }

    private PremiseConfig existsLeaf(String field) {
        return PremiseConfig.builder().field(field).conditionOperator("exists").build();
    }

    private void validateDependsOn(Class<?> taskClass,
                                   FieldDefinition definition,
                                   Set<String> codes,
                                   List<String> errors) {
        AiDependsOn dependsOn = null;
        try {
            Field javaField = taskClass.getDeclaredField(definition.getCode());
            dependsOn = javaField.getAnnotation(AiDependsOn.class);
        } catch (NoSuchFieldException ignored) {
            // cannot happen — the definition was built from this field
        }
        if (dependsOn == null) {
            return;
        }
        for (String reference : dependsOn.value()) {
            if (isBlank(reference)) {
                errors.add(String.format("字段 '%s' 的 @AiDependsOn 引用了空字段名", definition.getCode()));
            } else if (reference.equals(definition.getCode())) {
                errors.add(String.format("字段 '%s' 的 @AiDependsOn 不能依赖自己", definition.getCode()));
            } else if (!codes.contains(reference)) {
                errors.add(String.format("字段 '%s' 的 @AiDependsOn 引用了不存在的字段 '%s'",
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
     * Source must be one of: $value / $rawValue / $data.xxx (non-blank key,
     * no whitespace) / a literal constant (no $ prefix). Any other $-prefixed
     * expression is rejected — it is almost certainly a typo that would
     * otherwise silently map to null.
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
        return null; // literal constant
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
