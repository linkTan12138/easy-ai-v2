package com.link.easyai.starter.engine.normalization;

import com.link.easyai.starter.engine.AiTaskConfigService;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.config.NormalizationConfig;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link NormalizationEngine}.
 * <p>
 * For every field whose state is VALID/CONFIRMED and whose definition has a
 * {@link NormalizationConfig}, the matching {@link FieldNormalizer} bean
 * (looked up by {@code type()}) is executed:
 * <ul>
 *   <li>success → FieldState.value is replaced by the normalized value and the
 *       normalizer's data map is merged into FieldState.data</li>
 *   <li>failure → the field is marked INVALID with error code NORMALIZATION_FAILED,
 *       so the next turn will re-collect it</li>
 *   <li>no normalizer registered for the configured type → the field keeps its
 *       validated value (graceful degradation, consistent with the SPI pattern)</li>
 * </ul>
 * Normalization runs AFTER validation and BEFORE mapping, exactly once per field
 * (already-normalized fields are skipped via a data flag).
 */
@Component
public class DefaultNormalizationEngine implements NormalizationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultNormalizationEngine.class);

    /** Marker stored in FieldState.data when a field has been normalized */
    public static final String NORMALIZED_FLAG = "__normalized";

    private final Map<String, FieldNormalizer> normalizers;
    private final AiTaskConfigService configService;

    @Autowired
    public DefaultNormalizationEngine(List<FieldNormalizer> normalizerBeans,
                                      AiTaskConfigService configService) {
        this.normalizers = normalizerBeans == null ? Map.of()
                : normalizerBeans.stream()
                        .collect(Collectors.toMap(FieldNormalizer::type, n -> n, (a, b) -> a));
        this.configService = configService;
        log.info("[NormalizationEngine] registered {} field normalizers: {}",
                normalizers.size(), normalizers.keySet());
    }

    @Override
    public void normalize(TaskState state, TaskContext context) {
        if (state == null || state.getFields() == null || state.getFields().isEmpty()) {
            return;
        }

        AiTaskConfig config = loadConfig(state);
        if (config == null || config.getFields() == null) {
            return;
        }

        // Iterate over a copy: we mutate state.fields inside the loop
        for (Map.Entry<String, FieldState> entry : new LinkedHashMap<>(state.getFields()).entrySet()) {
            String fieldCode = entry.getKey();
            FieldState fieldState = entry.getValue();

            if (fieldState.getStatus() != FieldStatus.VALID
                    && fieldState.getStatus() != FieldStatus.CONFIRMED) {
                continue; // only normalize validated fields
            }
            if (fieldState.getData() != null && fieldState.getData().containsKey(NORMALIZED_FLAG)) {
                continue; // already normalized in a previous turn
            }

            FieldDefinition definition = config.getField(fieldCode);
            if (definition == null || definition.getNormalization() == null) {
                continue; // nothing to normalize
            }

            normalizeField(definition, fieldState, state, context);
        }
    }

    /**
     * Normalize a single field and update its state.
     */
    private void normalizeField(FieldDefinition definition,
                                FieldState fieldState,
                                TaskState state,
                                TaskContext context) {
        NormalizationConfig normalization = definition.getNormalization();
        FieldNormalizer normalizer = normalizers.get(normalization.getType());

        if (normalizer == null) {
            log.warn("[NormalizationEngine] no normalizer registered for type '{}' (field '{}'), "
                    + "keeping validated value", normalization.getType(), definition.getCode());
            return;
        }

        FieldContext fieldContext = buildFieldContext(definition, state, context);
        NormalizationResult result;
        try {
            result = normalizer.normalize(fieldState.getValue(), fieldContext, normalization.getParams());
        } catch (Exception e) {
            log.error("[NormalizationEngine] normalizer '{}' threw exception (field '{}')",
                    normalization.getType(), definition.getCode(), e);
            result = NormalizationResult.fail("标准化处理异常: " + e.getMessage());
        }

        if (result != null && result.isSuccess()) {
            Map<String, Object> data = new HashMap<>();
            if (fieldState.getData() != null) {
                data.putAll(fieldState.getData());
            }
            if (result.getData() != null) {
                data.putAll(result.getData());
            }
            data.put(NORMALIZED_FLAG, true);

            FieldState updated = fieldState.toBuilder()
                    .value(result.getValue() != null ? result.getValue() : fieldState.getValue())
                    .data(data)
                    .version(fieldState.getVersion() == null ? 1 : fieldState.getVersion() + 1)
                    .build();
            state.putField(definition.getCode(), updated);
            log.debug("[NormalizationEngine] field '{}' normalized: {} -> {}",
                    definition.getCode(), fieldState.getValue(), updated.getValue());
        } else {
            String errorMessage = result != null ? result.getErrorMessage() : "标准化返回空结果";
            log.info("[NormalizationEngine] field '{}' normalization failed: {}",
                    definition.getCode(), errorMessage);
            FieldState updated = fieldState.toBuilder()
                    .status(FieldStatus.INVALID)
                    .value(null)
                    .errorCode("NORMALIZATION_FAILED")
                    .errorMessage(errorMessage)
                    .version(fieldState.getVersion() == null ? 1 : fieldState.getVersion() + 1)
                    .build();
            state.putField(definition.getCode(), updated);
        }
    }

    /**
     * Load the task config for the state's bound version.
     */
    private AiTaskConfig loadConfig(TaskState state) {
        try {
            return configService.get(state.getTaskType(), state.getConfigVersion());
        } catch (Exception e) {
            log.warn("[NormalizationEngine] cannot load config for taskType={}, version={}: {}",
                    state.getTaskType(), state.getConfigVersion(), e.getMessage());
            return null;
        }
    }

    /**
     * Build the per-field context (mirrors DefaultValidationEngine).
     */
    private FieldContext buildFieldContext(FieldDefinition field,
                                           TaskState state,
                                           TaskContext context) {
        Map<String, Object> taskContextMap = new HashMap<>();
        if (context != null) {
            if (context.getTenantId() != null) {
                taskContextMap.put("tenantId", context.getTenantId());
            }
            if (context.getUserDetails() != null) {
                taskContextMap.put("userDetails", context.getUserDetails());
            }
            if (context.getData() != null) {
                taskContextMap.putAll(context.getData());
            }
        }

        return FieldContext.builder()
                .taskId(state.getTaskId())
                .taskType(state.getTaskType())
                .configVersion(state.getConfigVersion())
                .fieldCode(field.getCode())
                .fieldDefinition(field)
                .taskContext(taskContextMap)
                .fieldStates(state.getFields() != null
                        ? new LinkedHashMap<>(state.getFields())
                        : new LinkedHashMap<>())
                .build();
    }
}
