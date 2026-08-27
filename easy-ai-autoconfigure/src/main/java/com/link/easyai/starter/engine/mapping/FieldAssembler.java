package com.link.easyai.starter.engine.mapping;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.context.FieldContext;
import com.link.easyai.starter.engine.validation.ValidationResult;

import java.util.List;

/**
 * Assembles field values from a ValidationResult into a list of FieldValues.
 * <p>
 * A single field may map to multiple targets (e.g. channel name -> both
 * receiveChannelId and receiveChannelName).
 */
public interface FieldAssembler {

    /**
     * Assemble the validated field into one or more mapped values.
     *
     * @param definition the field definition (contains mapping rules)
     * @param result     the validation result (contains rawValue, value, data)
     * @param context    the field context
     * @return list of field values to be placed in action parameters
     */
    List<FieldValue> assemble(FieldDefinition definition, ValidationResult result, FieldContext context);
}
