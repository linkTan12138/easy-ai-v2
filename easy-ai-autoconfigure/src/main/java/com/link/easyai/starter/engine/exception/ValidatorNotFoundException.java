package com.link.easyai.starter.engine.exception;

/**
 * Thrown when a validator is not found in the registry.
 */
public class ValidatorNotFoundException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public ValidatorNotFoundException(String validatorType) {
        super("VALIDATOR_NOT_FOUND",
                "Validator not found in registry: " + validatorType +
                ". Make sure the validator is annotated with @AiValidator and registered as a Spring bean.");
    }
}
