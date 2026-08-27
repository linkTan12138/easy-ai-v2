package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * A single mapping rule: maps a source expression to a target path.
 * <p>
 * Supported source expressions in v1:
 * <ul>
 *   <li>$value    - the validated/normalized field value</li>
 *   <li>$rawValue - the raw LLM-extracted value</li>
 *   <li>$data.xxx - a value from the validation data map</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * { "target": "info.receiveChannelId", "source": "$data.id" }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRule {

    /** Target path in the action parameter map, e.g. "info.receiveChannelId" */
    private String target;

    /** Source expression: $value, $rawValue, $data.xxx */
    private String source;

    /** Optional transform name for future use (v1 ignores this) */
    private String transform;
}
