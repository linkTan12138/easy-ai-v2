package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Definition of a single validator in a field's validation pipeline.
 * The "type" maps to a registered {@link com.link.easyai.starter.engine.validation.FieldValidator}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatorDefinition {

    /** Validator type identifier, e.g. "ENUM", "CUSTOMER_EXIST", "CHANNEL_EXIST" */
    private String type;

    /** Parameters passed to the validator, e.g. {"channelType": "receive"} */
    private Map<String, Object> params;
}
