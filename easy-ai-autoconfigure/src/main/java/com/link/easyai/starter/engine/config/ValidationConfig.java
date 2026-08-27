package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Validation configuration for a field.
 * Validators run as a pipeline: each validator receives the output of the previous one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationConfig {

    /** Validation mode: currently only "PIPELINE" is supported */
    @Builder.Default
    private String mode = "PIPELINE";

    /** Ordered list of validators to execute */
    private List<ValidatorDefinition> validators;

    /** What to do on validation failure: "RETRY" (ask user again) or "BLOCK" (halt task) */
    @Builder.Default
    private String onFail = "RETRY";
}
