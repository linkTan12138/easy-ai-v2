package com.link.easyai.starter.engine.normalization;

import com.link.easyai.starter.engine.context.FieldContext;

import java.util.Map;

/**
 * Normalizes a validated field value into a standard form.
 * <p>
 * Example: "PI966" -> "PI966 外置锂电", "美国" -> "US"
 * <p>
 * Simple standardization (like enum label-to-value mapping) can be done inside
 * the Validator itself. This interface is for complex standardization that
 * benefits from being a separate, pluggable step.
 * <p>
 * Implementations should be annotated with @Component and discovered by the
 * NormalizationEngine.
 */
public interface FieldNormalizer {

    /**
     * Get the type identifier for this normalizer.
     * E.g. "CARGO_DESC", "COUNTRY_CODE"
     */
    String type();

    /**
     * Normalize the validated value into a standard form.
     *
     * @param value   the validated value
     * @param context the field context
     * @param params  normalizer parameters from configuration
     * @return normalization result
     */
    NormalizationResult normalize(Object value, FieldContext context, Map<String, Object> params);
}
