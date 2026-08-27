package com.link.easyai.starter.engine.exception;

/**
 * Thrown when an action executor is not found in the registry.
 */
public class ActionNotFoundException extends AiTaskException {

    private static final long serialVersionUID = 1L;

    public ActionNotFoundException(String actionType) {
        super("ACTION_NOT_FOUND",
                "Action not found in registry: " + actionType +
                ". Make sure the action is annotated with @AiAction and registered as a Spring bean.");
    }
}
