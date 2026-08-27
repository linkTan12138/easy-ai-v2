package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.premise.PremiseEngine;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.FieldStatus;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of FieldSelector.
 * <p>
 * A field participates in the current extraction round when:
 * 1. It is not yet completed (status is PENDING, INVALID, or no state exists)
 * 2. Its premise evaluates to true (or it has no premise)
 * <p>
 * Fields are returned in order of their "order" property (ascending, nulls last).
 * This replaces the old behavior of sending ALL field definitions to the LLM every turn.
 */
@Component
public class DefaultFieldSelector implements FieldSelector {

    private static final Logger log = LoggerFactory.getLogger(DefaultFieldSelector.class);

    private final PremiseEngine premiseEngine;

    @Autowired
    public DefaultFieldSelector(PremiseEngine premiseEngine) {
        this.premiseEngine = premiseEngine;
    }

    @Override
    public List<FieldDefinition> select(AiTaskConfig config, TaskState state) {
        List<FieldDefinition> result = new ArrayList<>();

        if (config.getFields() == null) {
            return result;
        }

        for (FieldDefinition field : config.getFields()) {
            FieldState fieldState = state.getField(field.getCode());

            // Skip if field is already completed (VALID, CONFIRMED, SKIPPED)
            if (fieldState != null && fieldState.isCompleted()) {
                continue;
            }

            // Check premise — if premise is not met, skip this field
            if (!premiseEngine.evaluate(field.getPremise(), state)) {
                log.debug("[FieldSelector] field {} skipped: premise not met", field.getCode());
                continue;
            }

            result.add(field);
        }

        // Sort by order (ascending, nulls last)
        result.sort(Comparator.comparing(
                FieldDefinition::getOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return result;
    }
}
