package com.link.easyai.starter.engine.action;

import com.link.easyai.starter.engine.context.ActionContext;

/**
 * Post-action: executed AFTER the main action succeeds.
 * <p>
 * Use cases: write track, send notification, push message, register issue.
 * <p>
 * Implementations should be annotated with @AiPostAction and registered as Spring beans.
 * <p>
 * Example:
 * <pre>
 * @AiPostAction("WRITE_TRACK")
 * public class WriteTrackPostAction implements PostActionExecutor { ... }
 * </pre>
 */
public interface PostActionExecutor {

    /**
     * Get the type identifier for this post-action.
     * E.g. "WRITE_TRACK", "SEND_MESSAGE"
     */
    String type();

    /**
     * Execute the post-action.
     *
     * @param context the action context (same as main action, plus main action result)
     */
    void execute(ActionContext context);
}
