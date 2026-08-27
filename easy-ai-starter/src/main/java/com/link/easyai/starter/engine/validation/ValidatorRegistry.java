package com.link.easyai.starter.engine.validation;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link FieldValidator} beans.
 * <p>
 * On startup, all beans implementing FieldValidator are collected and registered
 * by their type() identifier. The ValidationEngine looks up validators by type here.
 * <p>
 * To add a new validator:
 * <pre>
 * @Component
 * public class MyValidator implements FieldValidator {
 *     public String type() { return "MY_VALIDATOR"; }
 *     public ValidationResult validate(Object rawValue, FieldContext context, Map<String, Object> params) { ... }
 * }
 * </pre>
 * No changes to ValidationEngine or this registry are needed.
 */
@Component
public class ValidatorRegistry {

    private final Map<String, FieldValidator> validators = new ConcurrentHashMap<>();

    /**
     * Register a validator by its type identifier.
     */
    public void register(FieldValidator validator) {
        validators.put(validator.type(), validator);
    }

    /**
     * Get a validator by type.
     *
     * @return the validator, or null if not found
     */
    public FieldValidator get(String type) {
        return validators.get(type);
    }

    /**
     * Check if a validator type is registered.
     */
    public boolean contains(String type) {
        return validators.containsKey(type);
    }
}
