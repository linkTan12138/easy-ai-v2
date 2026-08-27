package com.link.easyai.starter.engine.mapping;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * A single mapped field value to be assembled into action parameters.
 * <p>
 * Example: target="info.receiveChannelId", value=123
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldValue {

    /** Target path in the action parameter map, e.g. "info.receiveChannelId" */
    private String target;

    /** The value to set at the target path */
    private Object value;
}
