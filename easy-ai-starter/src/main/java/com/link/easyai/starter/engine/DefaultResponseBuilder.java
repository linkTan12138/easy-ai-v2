package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.premise.PremiseEngine;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ResponseBuilder}.
 * <p>
 * Builds user-facing messages:
 * <ul>
 *   <li><b>needMore</b>: Lists collected fields, shows which fields are still
 *       needed, and includes error messages for fields that failed validation.</li>
 *   <li><b>done</b>: Uses the action result's message directly, or falls back
 *       to a default completion message.</li>
 * </ul>
 */
@Component
public class DefaultResponseBuilder implements ResponseBuilder {

    private static final Logger log = LoggerFactory.getLogger(DefaultResponseBuilder.class);

    private final PremiseEngine premiseEngine;

    public DefaultResponseBuilder(PremiseEngine premiseEngine) {
        this.premiseEngine = premiseEngine;
    }

    @Override
    public String buildNeedMore(AiTaskConfig config, TaskState state) {
        if (config == null || state == null) {
            return "请继续提供所需参数。";
        }

        StringBuilder sb = new StringBuilder();
        boolean hasContent = false;

        // 任务名称（从 @AiTask.name() 映射），显示在最上方
        String taskName = config.getName();
        if (taskName != null && !taskName.isBlank()) {
            sb.append(taskName).append("\n");
        }

        // Section 1: Already collected fields
        List<String> collected = collectCompletedFieldNames(config, state);
        if (!collected.isEmpty()) {
            hasContent = true;
            if (sb.length() > 0) sb.append("\n");
            sb.append("已收集：\n");
            for (String name : collected) {
                sb.append("  ✓ ").append(name).append("\n");
            }
        }

        // Section 2: Fields with validation errors
        List<String> errors = collectErrorFields(config, state);
        if (!errors.isEmpty()) {
            hasContent = true;
            if (sb.length() > 0) sb.append("\n");
            sb.append("以下参数需要修正：\n");
            for (String error : errors) {
                sb.append("  ⚠ ").append(error).append("\n");
            }
        }

        // Section 3: Still needed fields
        List<String> needed = collectPendingFieldNames(config, state);
        if (!needed.isEmpty()) {
            hasContent = true;
            if (sb.length() > 0) sb.append("\n");
            sb.append("请提供以下参数：\n");
            for (String name : needed) {
                sb.append("  - ").append(name).append("\n");
            }
        }

        // 没有实际内容（已收集/错误/待提供都为空）
        if (!hasContent) {
            if (taskName != null && !taskName.isBlank()) {
                return taskName + "\n\n请继续提供所需参数。";
            }
            return "请继续提供所需参数。";
        }

        return sb.toString().trim();
    }

    @Override
    public String buildDone(ActionResult actionResult) {
        if (actionResult == null) {
            return "任务已完成。";
        }

        if (actionResult.isSuccess()) {
            if (actionResult.getMessage() != null && !actionResult.getMessage().isBlank()) {
                return actionResult.getMessage();
            }
            return "任务已完成。";
        } else {
            // Action failed
            String msg = actionResult.getErrorMessage();
            if (msg == null || msg.isBlank()) {
                msg = actionResult.getMessage();
            }
            if (msg == null || msg.isBlank()) {
                msg = "任务执行失败，请重试。";
            }
            return msg;
        }
    }

    /**
     * Collect the human-readable names of fields that are completed (VALID, CONFIRMED, SKIPPED).
     */
    private List<String> collectCompletedFieldNames(AiTaskConfig config, TaskState state) {
        List<String> result = new ArrayList<>();
        if (state.getFields() == null) return result;

        for (var entry : state.getFields().entrySet()) {
            FieldState fs = entry.getValue();
            if (fs != null && fs.isCompleted()) {
                String name = resolveFieldName(config, entry.getKey(), fs);
                String displayValue = formatValue(fs);
                result.add(name + (displayValue != null ? ": " + displayValue : ""));
            }
        }
        return result;
    }

    /**
     * Collect error messages for fields that failed validation (INVALID status).
     */
    private List<String> collectErrorFields(AiTaskConfig config, TaskState state) {
        List<String> result = new ArrayList<>();
        if (state.getFields() == null) return result;

        for (var entry : state.getFields().entrySet()) {
            FieldState fs = entry.getValue();
            if (fs != null && fs.getStatus() == FieldStatus.INVALID) {
                String name = resolveFieldName(config, entry.getKey(), fs);
                String error = fs.getErrorMessage();
                if (error == null || error.isBlank()) {
                    error = "该参数无效";
                }
                result.add(name + " — " + error);
            }
        }
        return result;
    }

    /**
     * Collect the human-readable names of fields that are still needed.
     * A field is "needed" if it is PENDING, EXTRACTED, or not present in state,
     * AND it is either required or listed in requiredFields.
     */
    private List<String> collectPendingFieldNames(AiTaskConfig config, TaskState state) {
        List<String> result = new ArrayList<>();

        if (config.getFields() == null) return result;

        // Build a set of required field codes
        java.util.Set<String> requiredCodes = new java.util.HashSet<>();
        CompletionConfig completion = config.getCompletion();
        if (completion != null && completion.getRequiredFields() != null) {
            requiredCodes.addAll(completion.getRequiredFields());
        }

        for (FieldDefinition fd : config.getFields()) {
            if (fd.isRequired()) {
                requiredCodes.add(fd.getCode());
            }
        }

        // Check each required field — if not completed and premise is met, it's needed
        for (String code : requiredCodes) {
            FieldDefinition fd = config.getField(code);
            // Skip fields whose premise is not yet satisfied (e.g. @AiDependsOn)
            if (fd != null && !premiseEngine.evaluate(fd.getPremise(), state)) {
                continue;
            }
            FieldState fs = state.getField(code);
            if (fs == null || !fs.isCompleted()) {
                String name = fd != null && fd.getName() != null ? fd.getName() : code;
                result.add(name);
            }
        }

        // Also add optional fields that have been extracted but are not yet valid
        // (they were mentioned but need user attention)
        if (state.getFields() != null) {
            for (var entry : state.getFields().entrySet()) {
                FieldState fs = entry.getValue();
                if (fs != null && fs.getStatus() == FieldStatus.EXTRACTED) {
                    FieldDefinition fd = config.getField(entry.getKey());
                    String name = fd != null && fd.getName() != null ? fd.getName() : entry.getKey();
                    if (!result.contains(name)) {
                        result.add(name);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Resolve a human-readable name for a field code.
     * Tries the config's field definition first, falls back to the code itself.
     */
    private String resolveFieldName(AiTaskConfig config, String fieldCode, FieldState fieldState) {
        if (config.getFields() != null) {
            for (FieldDefinition fd : config.getFields()) {
                if (fieldCode.equals(fd.getCode())) {
                    return fd.getName() != null ? fd.getName() : fieldCode;
                }
            }
        }
        return fieldCode;
    }

    /**
     * Format a field value for display.
     * Priority: displayValue (human-readable) -> rawValue (original LLM text) -> value (standard).
     */
    private String formatValue(FieldState fs) {
        if (fs == null) return null;
        if (fs.getDisplayValue() != null && !fs.getDisplayValue().isBlank()) {
            return fs.getDisplayValue();
        }
        if (fs.getRawValue() != null) {
            return String.valueOf(fs.getRawValue());
        }
        if (fs.getValue() != null) {
            return String.valueOf(fs.getValue());
        }
        return null;
    }
}
