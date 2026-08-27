package com.link.easyai.starter.engine.mapping;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;

import java.util.Map;

/**
 * Runs the mapping engine: for each completed field, use its mapping rules
 * to assemble the action parameters map.
 * <p>
 * Supported source expressions in v1:
 * <ul>
 *   <li>$value    - the validated/normalized field value</li>
 *   <li>$rawValue - the raw LLM-extracted value</li>
 *   <li>$data.xxx - a value from the validation data map</li>
 * </ul>
 */
public interface MappingEngine {

    /**
     * Assemble all field values into an action parameter map.
     *
     * @param config  the task config (for field definitions and mapping rules)
     * @param state   the current task state (contains validated field values)
     * @param context the task context
     * @return map of target-path -> value, e.g. {"info.receiveChannelId": 123}
     */
    Map<String, Object> assemble(AiTaskConfig config, TaskState state, TaskContext context);
}
