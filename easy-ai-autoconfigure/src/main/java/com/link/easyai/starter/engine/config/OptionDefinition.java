package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * An enum/option definition for a field.
 * Example: {"label": "买单报关", "value": 3}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionDefinition {

    /** Display label shown to user */
    private String label;

    /** Actual value used internally */
    private Object value;
}
