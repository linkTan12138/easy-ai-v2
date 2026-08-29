package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.ExtractionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.OptionDefinition;
import com.link.easyai.starter.engine.history.ChatMessage;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link PromptBuilder}.
 * <p>
 * Builds a structured system prompt from the pending field definitions:
 * <ul>
 *   <li>context variables injected from {@link ExtractionContextProvider} (only
 *       those declared via {@code @AiExtract(contextVars = {...})}</li>
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

    private static final Logger log = LoggerFactory.getLogger(DefaultPromptBuilder.class);

    private final List<ExtractionContextProvider> contextProviders;

    public DefaultPromptBuilder(ObjectProvider<ExtractionContextProvider> providers) {
        this.contextProviders = providers.stream().toList();
        if (!contextProviders.isEmpty()) {
            log.info("[PromptBuilder] registered {} ExtractionContextProvider(s)", contextProviders.size());
        }
    }

    @Override
    public String build(List<FieldDefinition> pendingFields,
                        List<FieldDefinition> allFields,
                        TaskState state,
                        List<ChatMessage> chatHistory) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个信息抽取助手。请从用户消息中抽取下列字段的值，")
          .append("只输出一个 JSON 对象，不要输出任何其他文字。输出格式：")
          .append("{\"fields\": {\"字段代码\": 抽取的值, ...}, \"reason\": \"抽取依据的简短说明\"}\n\n");

        // 上下文变量（只注入 pendingFields 中声明的变量）
        String contextBlock = buildContextBlock(pendingFields);
        if (!contextBlock.isEmpty()) {
            sb.append(contextBlock).append("\n");
        }

        // 对话历史（如有），放在字段列表之前，帮助LLM理解上下文指代
        if (chatHistory != null && !chatHistory.isEmpty()) {
            sb.append("以下是本次对话的历史记录（仅供上下文参考，注意用户可能用\"刚才那个\"、\"上面说的\"等指代历史中的信息）：\n");
            for (ChatMessage msg : chatHistory) {
                String role = "user".equalsIgnoreCase(msg.getRole()) ? "用户" : "AI";
                sb.append("[").append(role).append("]：").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("待抽取字段列表：\n");
        int index = 1;
        for (FieldDefinition field : allFields) {
            sb.append(index++).append(". ").append(fieldLine(field)).append('\n');
        }

        sb.append('\n').append(extractionRules(pendingFields));

        sb.append("\n要求：\n")
          .append("- 如果用户消息中没有某个字段的值，就不要在 fields 中输出该字段（不要编造）。\n")
          .append("- 保持用户原始表述，不要自行翻译或改写；枚举字段输出用户提到的选项 label 或 value 均可。\n")
          .append("- 只输出 JSON，不要使用 markdown 代码块包裹。\n");

        String prompt = sb.toString();
        log.debug("[PromptBuilder] built extraction prompt:\n{}", prompt);
        return prompt;
    }

    /**
     * 构建上下文变量块。
     * 收集所有 pendingFields 中声明的 contextVars，从 Provider 中取值，
     * 只注入声明过且有值的变量。
     */
    private String buildContextBlock(List<FieldDefinition> pendingFields) {
        if (contextProviders.isEmpty() || pendingFields == null || pendingFields.isEmpty()) {
            return "";
        }

        // 收集所有 pendingFields 声明的变量名（去重，保持顺序）
        Set<String> requiredVars = new LinkedHashSet<>();
        for (FieldDefinition field : pendingFields) {
            ExtractionConfig extraction = field.getExtraction();
            if (extraction != null && extraction.getContextVars() != null) {
                requiredVars.addAll(extraction.getContextVars());
            }
        }
        if (requiredVars.isEmpty()) {
            return "";
        }

        // 从所有 Provider 中收集变量值
        Map<String, String> variablePool = new HashMap<>();
        for (ExtractionContextProvider provider : contextProviders) {
            try {
                Map<String, String> vars = provider.getContextVariables();
                if (vars != null) {
                    variablePool.putAll(vars);
                }
            } catch (Exception e) {
                log.warn("[PromptBuilder] ExtractionContextProvider {} 抛出异常: {}",
                        provider.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 只取声明过的变量，且值非空
        StringBuilder sb = new StringBuilder();
        sb.append("以下上下文信息仅供抽取参考：\n");
        boolean hasAny = false;
        for (String varName : requiredVars) {
            String value = variablePool.get(varName);
            if (value != null && !value.isBlank()) {
                sb.append("- ").append(varName).append(": ").append(value).append("\n");
                hasAny = true;
            } else {
                log.warn("[PromptBuilder] 字段声明了上下文变量 '{}'，但没有任何 Provider 提供该变量", varName);
            }
        }
        if (!hasAny) {
            return "";
        }
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
