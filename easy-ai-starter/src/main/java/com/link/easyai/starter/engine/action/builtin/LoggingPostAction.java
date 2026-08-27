package com.link.easyai.starter.engine.action.builtin;

import com.link.easyai.starter.engine.action.AiPostAction;
import com.link.easyai.starter.engine.action.PostActionExecutor;
import com.link.easyai.starter.engine.context.ActionContext;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in post-action: writes an audit log line after the main action succeeds.
 * <p>
 * Register in config via:
 * <pre>
 * "action": { "type": "UPDATE_WAYBILL", "postActions": ["LOG"] }
 * </pre>
 * Purely framework-level (no business dependency); useful for tracing task
 * completion in production logs. The engine itself owns the terminal
 * {@link TaskStatus#COMPLETED} transition — this post-action only audits.
 */
@AiPostAction("LOG")
public class LoggingPostAction implements PostActionExecutor {

    private static final Logger auditLog = LoggerFactory.getLogger("AI_TASK_AUDIT");

    @Override
    public String type() {
        return "LOG";
    }

    @Override
    public void execute(ActionContext context) {
        auditLog.info("[Audit] AI task completed: taskType={}, taskId={}, taskStatus={}, parameters={}",
                context.getConfig() != null ? context.getConfig().getTaskType() : null,
                context.getTaskId(),
                context.getState() != null ? context.getState().getStatus() : null,
                context.getParameters());
    }
}
