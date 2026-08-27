package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.TaskState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PromptBuilder}.
 * <p>
 * Builds a structured system prompt from the pending field definitions:
 * <ul>
 *   <li>each field's code / name / type / description / examples / rules / options</li>
 *   <li>a summary of already-collected fields (so the LLM understands context
 *       and can correct them when the user re-provides a value)</li>
 *   <li>a strict JSON-only output contract</li>
 * </ul>
 * Only pending fields are sent to the LLM — never the whole field set — to keep
 * token usage low and extraction focused.
 */
@Component
public class DefaultPromptBuilder implements PromptBuilder {

    @Override
    public String build(List<FieldDefinition> pendingFields,
                        List<FieldDefinition> allFields,
                        TaskState state) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个信息抽取助手。请从用户消息中抽取下列字段的值，")
          .append("只输出一个 JSON 对象，不要输出任何其他文字。输出格式：")
          .append("{\"fields\": {\"字段代码\": 抽取的值, ...}, \"reason\": \"抽取依据的简短说明\"}\n\n");

        sb.append("待抽取字段列表：\n");
        int index = 1;
        for (FieldDefinition field : allFields) {
            sb.append(index++).append(". ").append(fieldLine(field)).append('\n');
        }

        sb.append('\n').append(extractionRules(pendingFields));

        /*String collected = collectedSummary(state, allFields);
        if (!collected.isEmpty()) {
            sb.append("\n当前已收集字段值（仅作上下文参考，每行格式：字段代码（名称，说明）= 值）：\n")
              .append(collected).append('\n')
              .append("如果用户本次消息中重新提到了其中某个字段并给出新值——用户可能使用字段名称、")
              .append("别名、含义或示例值来指代该字段（例如用“订单号”“客户号”指代“客户单号”）——")
              .append("请视为更正/更新：以用户最新表述为准，把该字段连同新值输出到 fields 中覆盖旧值，")
              .append("并在 reason 中注明“用户更正/更新”。若用户只是重复确认旧值，则无需输出。\n");
        }*/

        sb.append("\n要求：\n")
          .append("- 如果用户消息中没有某个字段的值，就不要在 fields 中输出该字段（不要编造）。\n")
          .append("- 保持用户原始表述，不要自行翻译或改写；枚举字段输出用户提到的选项 label 或 value 均可。\n")
          .append("- 只输出 JSON，不要使用 markdown 代码块包裹。\n");

        return sb.toString();
    }

    /**
     * Describe a single field for the prompt.
     */
    private String fieldLine(FieldDefinition field) {
        StringBuilder sb = new StringBuilder();
        sb.append("字段代码: ").append(field.getCode())
          .append("，名称: ").append(field.getName() != null ? field.getName() : field.getCode());

        if (field.getType() != null) {
            sb.append("，类型: ").append(field.getType());
        }

        ExtractionConfig extraction = field.getExtraction();
        if (extraction != null) {
            if (extraction.getDescription() != null) {
                sb.append("，说明: ").append(extraction.getDescription());
            }
            if (extraction.getExamples() != null && !extraction.getExamples().isEmpty()) {
                sb.append("，示例: ").append(String.join(" / ", extraction.getExamples()));
            }
            if (extraction.getRules() != null) {
                for (String rule : extraction.getRules()) {
                    sb.append("，规则: ").append(rule);
                }
            }
        }

        if (field.getOptions() != null && !field.getOptions().isEmpty()) {
            String options = field.getOptions().stream()
                    .map(o -> String.valueOf(o.getLabel()))
                    .collect(Collectors.joining(" / "));
            sb.append("，可选值: ").append(options);
        }

        return sb.toString();
    }

    /**
     * Global extraction rules derived from field configs (e.g. allowEmpty).
     */
    private String extractionRules(List<FieldDefinition> pendingFields) {
        List<String> allowEmpty = pendingFields.stream()
                .filter(f -> f.getExtraction() != null && f.getExtraction().isAllowEmpty())
                .map(FieldDefinition::getCode)
                .toList();
        if (allowEmpty.isEmpty()) {
            return "";
        }
        return "允许为空的字段（可输出空字符串）: " + String.join(", ", allowEmpty) + "\n";
    }

    /**
     * One-line-per-field summary of already collected fields.
     * <p>
     * When the full field definitions are available, each line carries the
     * field's name and extraction description, e.g.
     * {@code customerNos（客户单号，说明: 需要修改订单的客户单号）= test1123}.
     * This lets the LLM map a re-provided value (possibly referred to by name,
     * alias or meaning, e.g. "订单号" for 客户单号) to the correct field.
     * Falls back to plain {@code code=value} when {@code allFields} is null.
     */
    private String collectedSummary(TaskState state, List<FieldDefinition> allFields) {
        if (state == null || state.getFields() == null || state.getFields().isEmpty()) {
            return "";
        }
        Map<String, FieldDefinition> byCode = new HashMap<>();
        if (allFields != null) {
            for (FieldDefinition field : allFields) {
                byCode.put(field.getCode(), field);
            }
        }
        return state.getFields().entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().isCompleted())
                .filter(e -> e.getValue().getValue() != null)
                .map(e -> collectedLine(e.getKey(), e.getValue().getValue(), byCode.get(e.getKey())))
                .collect(Collectors.joining("\n"));
    }

    private String collectedLine(String code, Object value, FieldDefinition definition) {
        if (definition == null) {
            return code + "=" + value;
        }
        StringBuilder sb = new StringBuilder(code).append("（");
        String name = definition.getName() != null ? definition.getName() : code;
        sb.append(name);
        ExtractionConfig extraction = definition.getExtraction();
        if (extraction != null && extraction.getDescription() != null
                && !extraction.getDescription().isBlank()) {
            sb.append("，说明: ").append(extraction.getDescription());
        }
        return sb.append("）=").append(value).toString();
    }
}
