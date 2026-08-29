package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.task.TaskExecuteEngine;
import com.link.easyai.starter.engine.task.TaskResult;
import com.link.easyai.starter.engine.completion.CompletionEngine;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.extraction.ExtractionEngine;
import com.link.easyai.starter.engine.extraction.ExtractionResult;
import com.link.easyai.starter.engine.extraction.FieldSelector;
import com.link.easyai.starter.engine.history.ChatMessage;
import com.link.easyai.starter.engine.history.ChatHistoryManager;
import com.link.easyai.starter.engine.mapping.MappingEngine;
import com.link.easyai.starter.engine.normalization.NormalizationEngine;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStateManager;
import com.link.easyai.starter.engine.state.TaskStatus;
import com.link.easyai.starter.engine.validation.ValidationEngine;
import com.link.easyai.starter.engine.observability.EngineMdcUtils;
import com.link.easyai.starter.engine.observability.EngineMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link AiTaskEngine}.
 * <p>
 * This is the orchestration heart of the framework. It wires together all
 * the sub-engines (extraction, validation, normalization, mapping, completion, action)
 * into the single-turn processing pipeline.
 * <p>
 * The engine also owns the {@link TaskStatus} lifecycle:
 * <pre>
 * INITIALIZED -> COLLECTING -> (READY) -> EXECUTING -> COMPLETED / FAILED
 *                   ^                          |
 *                   +------- action failed ----+
 * </pre>
 * Every exit path persists the state first, so an unexpected JVM crash never
 * loses progress.
 * <p>
 * Business code only needs to call:
 * <pre>
 * aiTaskEngine.execute("ORDER_UPDATE", taskId, userMessage, taskContext);
 * </pre>
 */
@Component
public class DefaultAiTaskEngine implements AiTaskEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiTaskEngine.class);

    private final AiTaskConfigService configService;
    private final TaskStateManager stateManager;
    private final FieldSelector fieldSelector;
    private final ExtractionEngine extractionEngine;
    private final ValidationEngine validationEngine;
    private final NormalizationEngine normalizationEngine;
    private final MappingEngine mappingEngine;
    private final CompletionEngine completionEngine;
    private final TaskExecuteEngine taskExecuteEngine;
    private final ResponseBuilder responseBuilder;
    private final EngineMetrics metrics;
    private final AiTaskProperties properties;
    private final ChatHistoryManager chatHistoryManager;

    @Autowired
    public DefaultAiTaskEngine(AiTaskConfigService configService,
                               TaskStateManager stateManager,
                               FieldSelector fieldSelector,
                               ExtractionEngine extractionEngine,
                               ValidationEngine validationEngine,
                               NormalizationEngine normalizationEngine,
                               MappingEngine mappingEngine,
                               CompletionEngine completionEngine,
                               TaskExecuteEngine taskExecuteEngine,
                               ResponseBuilder responseBuilder,
                               EngineMetrics metrics,
                               AiTaskProperties properties,
                               ChatHistoryManager chatHistoryManager) {
        this.configService = configService;
        this.stateManager = stateManager;
        this.fieldSelector = fieldSelector;
        this.extractionEngine = extractionEngine;
        this.validationEngine = validationEngine;
        this.normalizationEngine = normalizationEngine;
        this.mappingEngine = mappingEngine;
        this.completionEngine = completionEngine;
        this.taskExecuteEngine = taskExecuteEngine;
        this.responseBuilder = responseBuilder;
        this.metrics = metrics;
        this.properties = properties;
        this.chatHistoryManager = chatHistoryManager;
    }

    @Override
    public AiTaskResponse execute(String taskType, String taskId, String userMessage, TaskContext taskContext) {
        log.info("[AiTaskEngine] execute start: taskType={}, taskId={}", taskType, taskId);

        // MDC context for structured logging
        String tenantId = taskContext != null && taskContext.getTenantId() != null
                ? String.valueOf(taskContext.getTenantId()) : null;
        try (EngineMdcUtils.MdcScope mdc = EngineMdcUtils.withTaskContext(taskId, taskType, tenantId)) {

            // The whole pipeline is wrapped so a failing sub-engine never crashes
            // the caller's chat flow — we degrade to a "need more" error response
            // and persist whatever progress was made. The holder lets the catch
            // block persist the state that was already loaded when the error hit.
            TaskState[] stateHolder = new TaskState[1];
            try {
                AiTaskResponse response = doExecute(taskType, taskId, userMessage, taskContext, stateHolder);
                // Record chat metric
                metrics.recordChat(taskType, response.isCompleted() ? "completed" : "needMore");
                return response;
            } catch (Exception e) {
                log.error("[AiTaskEngine] unexpected error: taskType={}, taskId={}", taskType, taskId, e);
                TaskState state = stateHolder[0];
                if (state != null) {
                    try {
                        state.setStatus(TaskStatus.FAILED);
                        stateManager.save(state);
                    } catch (Exception saveEx) {
                        log.error("[AiTaskEngine] failed to persist state after error: taskId={}", taskId, saveEx);
                    }
                }
                metrics.recordChat(taskType, "error");
                return AiTaskResponse.needMore(taskId,
                        "抱歉，处理您的请求时出现异常，请稍后重试或重新描述您的需求。",
                        state);
            }
        }
    }

    private AiTaskResponse doExecute(String taskType, String taskId, String userMessage,
                                     TaskContext taskContext, TaskState[] stateHolder) {
        // 1. Load config (resolve version from existing state or latest published)
        TaskState existingState = stateManager.load(taskId, taskType, null);
        Integer configVersion = (existingState != null && existingState.getConfigVersion() != null)
                ? existingState.getConfigVersion()
                : configService.getLatestVersion(taskType);

        AiTaskConfig config = configService.get(taskType, configVersion);
        log.debug("[AiTaskEngine] config loaded: taskType={}, version={}", taskType, configVersion);

        // 2. Load or create task state
        TaskState state = stateManager.load(taskId, taskType, configVersion);
        stateHolder[0] = state;
        // Entering the pipeline: INITIALIZED (fresh) -> COLLECTING
        if (state.getStatus() == null || state.getStatus() == TaskStatus.INITIALIZED) {
            state.setStatus(TaskStatus.COLLECTING);
            // 新任务：从 TaskContext 中取出意图识别信息，持久化到任务记录（仅记录一次）
            if (taskContext != null) {
                state.setIntentReason(taskContext.getIntentReason());
                state.setIntentConfidence(taskContext.getIntentConfidence());
                state.setIntentSource(taskContext.getIntentSource());
            }
        }
        log.debug("[AiTaskEngine] state loaded: status={}, fields={}", state.getStatus(), state.getFields().size());

        // 2.5 Max turns lifecycle check
        int maxTurns = properties.getLifecycle().getMaxTurns();
        int currentTurns = state.getTurnCount() != null ? state.getTurnCount() : 0;
        if (currentTurns >= maxTurns && state.getStatus() == TaskStatus.COLLECTING) {
            log.warn("[AiTaskEngine] task {} exceeded max turns ({}), marking FAILED", taskId, maxTurns);
            state.setStatus(TaskStatus.FAILED);
            stateManager.save(state);
            return AiTaskResponse.needMore(taskId,
                    "已达到最大对话轮次（" + maxTurns + "轮），请重新发起任务。", state);
        }
        // Increment turn count
        state.setTurnCount(currentTurns + 1);

        // 3. Select pending fields (premise filtering)
        List<FieldDefinition> pendingFields = fieldSelector.select(config, state);
        log.debug("[AiTaskEngine] pending fields: {}", pendingFields.stream().map(FieldDefinition::getCode).toList());

        // If no pending fields and task is not complete, check completion
        if (pendingFields.isEmpty()) {
            if (completionEngine.completed(config, state)) {
                // All fields collected, execute action
                return executeAction(config, state, taskContext);
            } else {
                // Should not happen, but handle gracefully
                stateManager.save(state);
                return AiTaskResponse.needMore(taskId,
                        responseBuilder.buildNeedMore(config, state), state);
            }
        }

        // 4. Build prompt + call LLM + parse extraction
        // 加载当前任务的对话历史（按任务隔离），用于多轮上下文理解
        // 注意：不使用 session 级别的完整历史，避免上一个任务的历史污染当前任务的字段抽取
        String sessionId = taskContext != null ? taskContext.getSessionId() : null;
        String taskIdForHistory = taskContext != null ? taskContext.getTaskId() : null;
        List<ChatMessage> chatHistory;
        if (sessionId != null && !sessionId.isBlank() && taskIdForHistory != null && !taskIdForHistory.isBlank()) {
            chatHistory = chatHistoryManager.loadHistoryByTask(sessionId, taskIdForHistory);
        } else {
            chatHistory = java.util.Collections.emptyList();
        }
        ExtractionResult extraction = extractionEngine.extract(
                userMessage, pendingFields, config.getFields(), state, chatHistory);
        log.debug("[AiTaskEngine] extraction result: success={}, fields={}",
                extraction.isSuccess(), extraction.getFields());

        if (!extraction.isSuccess()) {
            // Extraction failed — save state and return error message
            stateManager.save(state);
            return AiTaskResponse.needMore(taskId, extraction.getErrorMessage(), state);
        }

        // 5. Validate fields (updates task state with field states)
        validationEngine.validate(extraction, config, state, taskContext);
        log.debug("[AiTaskEngine] validation done, field states: {}", state.getFields());

        // 6. Normalize values (for fields with normalization config)
        normalizationEngine.normalize(state, taskContext);

        // 7. Check completion
        if (!completionEngine.completed(config, state)) {
            // Not complete yet — save state and ask for more
            stateManager.save(state);
            String message = responseBuilder.buildNeedMore(config, state);
            return AiTaskResponse.needMore(taskId, message, state);
        }
        state.setStatus(TaskStatus.READY);

        // 8. All fields collected — execute action
        return executeAction(config, state, taskContext);
    }

    /**
     * Execute the action and return the final response.
     */
    private AiTaskResponse executeAction(AiTaskConfig config, TaskState state, TaskContext taskContext) {
        log.info("[AiTaskEngine] executing task: type={}", config.getExecuteConfig().getType());
        state.setStatus(TaskStatus.EXECUTING);

        // Assemble action parameters from field mapping
        Map<String, Object> parameters = mappingEngine.assemble(config, state, taskContext);
        log.debug("[AiTaskEngine] assembled parameters: {}", parameters);

        // Execute action + post-actions
        TaskResult taskResult = taskExecuteEngine.execute(config, state, parameters, taskContext);

        if (taskResult != null && taskResult.isSuccess()) {
            // Engine owns the terminal COMPLETED status (post-actions such as
            // LOG only add audit trail, they are optional)
            state.setStatus(TaskStatus.COMPLETED);
        } else {
            // Action failed — back to COLLECTING so the user can correct input
            // and the task keeps its progress for retry
            state.setStatus(TaskStatus.COLLECTING);
        }

        // Save final state
        stateManager.save(state);

        if (taskResult != null && taskResult.isSuccess()) {
            String message = responseBuilder.buildDone(taskResult);
            return AiTaskResponse.done(state.getTaskId(), message, taskResult, state);
        } else {
            String message = taskResult != null ? taskResult.getErrorMessage() : "任务执行失败";
            return AiTaskResponse.needMore(state.getTaskId(), message, state);
        }
    }
}
