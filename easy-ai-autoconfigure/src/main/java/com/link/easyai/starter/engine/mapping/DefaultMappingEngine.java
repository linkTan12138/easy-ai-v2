package com.link.easyai.starter.engine.mapping;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link MappingEngine}.
 * <p>
 * For every completed field (VALID / CONFIRMED / SKIPPED-with-value) that has
 * mapping rules, the {@link FieldAssembler} evaluates the rules and the results
 * are placed into a flat map keyed by target path, e.g.
 * <pre>
 * {"info.receiveChannelId": 123, "info.receiveChannelName": "DHL"}
 * </pre>
 * Later rules overwrite earlier ones at the same target path.
 */
@Component
public class DefaultMappingEngine implements MappingEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultMappingEngine.class);

    private final FieldAssembler fieldAssembler;

    @Autowired
    public DefaultMappingEngine(FieldAssembler fieldAssembler) {
        this.fieldAssembler = fieldAssembler;
    }

    @Override
    public Map<String, Object> assemble(AiTaskConfig config, TaskState state, TaskContext context) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (config == null || config.getFields() == null || state == null || state.getFields() == null) {
            return parameters;
        }

        for (FieldDefinition definition : config.getFields()) {
            if (definition == null || definition.getMappings() == null
                    || definition.getMappings().isEmpty()) {
                continue;
            }

            FieldState fieldState = state.getField(definition.getCode());
            if (fieldState == null || !fieldState.isCompleted()) {
                log.debug("[MappingEngine] field '{}' not completed, mapping skipped", definition.getCode());
                continue;
            }
            if (fieldState.getValue() == null) {
                // SKIPPED fields have no value — nothing to map
                continue;
            }

            ValidationResult result = ValidationResult.success(
                    fieldState.getRawValue(), fieldState.getValue(), fieldState.getData());

            List<FieldValue> values = fieldAssembler.assemble(definition, result,
                    buildFieldContext(definition, state, context));
            for (FieldValue value : values) {
                parameters.put(value.getTarget(), value.getValue());
            }
        }

        log.debug("[MappingEngine] assembled {} parameters: {}", parameters.size(), parameters.keySet());
        return parameters;
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
