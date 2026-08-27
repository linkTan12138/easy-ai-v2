package com.link.easyai.starter.engine.action;

import com.link.easyai.starter.engine.config.ActionConfig;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.context.ActionContext;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.state.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link ActionEngine}.
 * <p>
 * Execution order:
 * <ol>
 *   <li>Look up the main {@link ActionExecutor} by {@code config.action.type}
 *       in the {@link ActionRegistry}</li>
 *   <li>Build the {@link ActionContext} (parameters + state + config + extra params)</li>
 *   <li>Execute the main action</li>
 *   <li>If (and only if) the main action succeeded, execute all post-actions
 *       ({@code config.action.postActions}) in configured order</li>
 * </ol>
 * Post-actions are best-effort: a missing or failing post-action is logged and
 * skipped — it never fails the already-successful main action.
 */
@Component
public class DefaultActionEngine implements ActionEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultActionEngine.class);

    private final ActionRegistry actionRegistry;

    @Autowired
    public DefaultActionEngine(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    @Override
    public ActionResult execute(AiTaskConfig config,
                                TaskState state,
                                Map<String, Object> parameters,
                                TaskContext taskContext) {
        ActionConfig actionConfig = config != null ? config.getAction() : null;
        if (actionConfig == null || actionConfig.getType() == null || actionConfig.getType().isBlank()) {
            log.error("[ActionEngine] no action type configured for taskType={}",
                    config != null ? config.getTaskType() : null);
            return ActionResult.fail("ACTION_NOT_CONFIGURED", "任务未配置执行动作");
        }

        String type = actionConfig.getType();
        ActionExecutor executor = actionRegistry.getAction(type);
        if (executor == null) {
            log.error("[ActionEngine] action '{}' not registered", type);
            return ActionResult.fail("ACTION_NOT_FOUND", "执行动作未注册: " + type);
        }

        // Merge config-level extra params into the parameters (config params
        // never overwrite field-mapped values)
        Map<String, Object> mergedParameters = new HashMap<>();
        if (actionConfig.getParams() != null) {
            mergedParameters.putAll(actionConfig.getParams());
        }
        if (parameters != null) {
            mergedParameters.putAll(parameters);
        }

        ActionContext context = ActionContext.builder()
                .taskId(state != null ? state.getTaskId() : null)
                .config(config)
                .state(state)
                .parameters(mergedParameters)
                .taskContext(taskContext)
                .build();

        // Execute the main action
        ActionResult result;
        try {
            result = executor.execute(context);
        } catch (Exception e) {
            log.error("[ActionEngine] action '{}' threw exception", type, e);
            return ActionResult.fail("ACTION_ERROR", "动作执行异常: " + e.getMessage());
        }
        if (result == null) {
            log.error("[ActionEngine] action '{}' returned null", type);
            return ActionResult.fail("ACTION_ERROR", "动作执行返回空结果: " + type);
        }

        log.info("[ActionEngine] action '{}' finished: success={}", type, result.isSuccess());

        // Post-actions only run after a successful main action
        if (result.isSuccess()) {
            executePostActions(actionConfig, context);
        }
        return result;
    }

    /**
     * Execute the configured post-actions in order. Best-effort: failures are
     * logged but do not affect the main action result.
     */
    private void executePostActions(ActionConfig actionConfig, ActionContext context) {
        if (actionConfig.getPostActions() == null || actionConfig.getPostActions().isEmpty()) {
            return;
        }
        for (String postType : actionConfig.getPostActions()) {
            if (postType == null || postType.isBlank()) {
                continue;
            }
            PostActionExecutor postAction = actionRegistry.getPostAction(postType.trim());
            if (postAction == null) {
                log.warn("[ActionEngine] post-action '{}' not registered, skipped", postType);
                continue;
            }
            try {
                postAction.execute(context);
                log.debug("[ActionEngine] post-action '{}' done", postType);
            } catch (Exception e) {
                log.error("[ActionEngine] post-action '{}' failed (main action already succeeded, "
                        + "continuing)", postType, e);
            }
        }
    }
}
