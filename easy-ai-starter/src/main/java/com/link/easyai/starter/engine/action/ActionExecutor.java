package com.link.easyai.starter.engine.action;

import com.link.easyai.starter.engine.context.ActionContext;

/**
 * Executes the main business action when all fields are collected.
 * <p>
 * Implementations should be annotated with @AiAction and registered as Spring beans.
 * The ActionRegistry looks them up by type identifier.
 * <p>
 * Example:
 * <pre>
 * @AiAction("UPDATE_WAYBILL")
 * public class UpdateWaybillAction implements ActionExecutor { ... }
 * </pre>
 * <p>
 * The executor receives an ActionContext containing:
 * - The assembled parameters (from field mapping)
 * - The task state
 * - The task config
 * - The task context (tenant, user, etc.)
 */
public interface ActionExecutor {

    /**
     * Get the type identifier for this action.
     * E.g. "UPDATE_WAYBILL", "REGISTER_ISSUE"
     */
    String type();

    /**
     * Execute the business action.
     *
     * @param context the action context (parameters, state, config)
     * @return action result
     */
    ActionResult execute(ActionContext context);
}
