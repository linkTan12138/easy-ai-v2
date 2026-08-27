package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.Map;

/**
 * Normalization configuration for a field.
 * Complex standardization (e.g. "PI966" -> "PI966 外置锂电") is handled by a registered normalizer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizationConfig {

    /** Normalizer type identifier, e.g. "CARGO_DESC", "COUNTRY_CODE" */
    private String type;

    /** Parameters passed to the normalizer */
    private Map<String, Object> params;
}
