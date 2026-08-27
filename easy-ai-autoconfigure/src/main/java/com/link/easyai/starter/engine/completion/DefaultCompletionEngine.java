package com.link.easyai.starter.engine.completion;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.CompletionConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default implementation of {@link CompletionEngine}.
 * <p>
 * A task is considered complete when ALL of the following are true:
 * <ol>
 *   <li>Every field in {@link CompletionConfig#getRequiredFields()} has a
 *       status of VALID or CONFIRMED in the task state.</li>
 *   <li>Every field marked {@code required=true} in {@link FieldDefinition}
 *       (that is not already in requiredFields) has VALID or CONFIRMED status.</li>
 *   <li>Every field in {@link CompletionConfig#getOptionalFields()} is either
 *       VALID, CONFIRMED, SKIPPED, or absent from the state entirely.</li>
 *   <li>No field has a status of INVALID (those must be re-collected).</li>
 * </ol>
 * Fields not mentioned in the completion config and not marked required are
 * treated as "don't care" — their completion status does not affect the
 * overall task completion.
 */
@Component
public class DefaultCompletionEngine implements CompletionEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultCompletionEngine.class);

    @Override
    public boolean completed(AiTaskConfig config, TaskState state) {
        if (config == null || state == null) {
            return false;
        }

        // If no completion config at all, check all field definitions
        CompletionConfig completion = config.getCompletion();
        if (completion == null) {
            // Fallback: every required field definition must be completed
            return allRequiredFieldDefinitionsCompleted(config, state);
        }

        Set<String> requiredCodes = new HashSet<>();
        if (completion.getRequiredFields() != null) {
            requiredCodes.addAll(completion.getRequiredFields());
        }

        // Also include fields marked required=true in FieldDefinition
        if (config.getFields() != null) {
            for (FieldDefinition fd : config.getFields()) {
                if (fd.isRequired()) {
                    requiredCodes.add(fd.getCode());
                }
            }
        }

        // 1. Check all required fields are VALID or CONFIRMED
        for (String code : requiredCodes) {
            FieldState fs = state.getField(code);
            if (fs == null || !fs.isCompleted()) {
                log.debug("[CompletionEngine] required field '{}' is not yet completed", code);
                return false;
            }
        }

        // 2. Check optional fields — they must not be in INVALID state
        if (completion.getOptionalFields() != null) {
            for (String code : completion.getOptionalFields()) {
                FieldState fs = state.getField(code);
                if (fs != null && fs.getStatus() != null) {
                    // VALID, CONFIRMED, SKIPPED are all acceptable
                    // PENDING, EXTRACTED, INVALID mean the field still needs attention
                    switch (fs.getStatus()) {
                        case VALID:
                        case CONFIRMED:
                        case SKIPPED:
                            break;
                        default:
                            log.debug("[CompletionEngine] optional field '{}' is in status '{}', not complete",
                                    code, fs.getStatus());
                            return false;
                    }
                }
                // If fs is null or has no status, the optional field was never collected — that's OK
            }
        }

        // 3. Check no field in state is INVALID (it needs re-collection)
        if (state.getFields() != null) {
            for (var entry : state.getFields().entrySet()) {
                FieldState fs = entry.getValue();
                if (fs != null && fs.getStatus() == com.link.easyai.starter.engine.state.FieldStatus.INVALID) {
                    log.debug("[CompletionEngine] field '{}' is INVALID, task not complete", entry.getKey());
                    return false;
                }
            }
        }

        log.debug("[CompletionEngine] all checks passed, task is complete");
        return true;
    }

    /**
     * Fallback completion check when no CompletionConfig is defined.
     * All required field definitions must be completed.
     */
    private boolean allRequiredFieldDefinitionsCompleted(AiTaskConfig config, TaskState state) {
        if (config.getFields() == null) return true;
        for (FieldDefinition fd : config.getFields()) {
            if (fd.isRequired()) {
                FieldState fs = state.getField(fd.getCode());
                if (fs == null || !fs.isCompleted()) {
                    log.debug("[CompletionEngine] (fallback) required field '{}' not completed", fd.getCode());
                    return false;
                }
            }
        }
        return true;
    }
}
