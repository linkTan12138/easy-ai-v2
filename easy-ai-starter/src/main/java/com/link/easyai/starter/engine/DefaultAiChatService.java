package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiChatSession;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.intent.IntentEngine;
import com.link.easyai.starter.engine.intent.IntentResult;
import com.link.easyai.starter.engine.lock.TaskLockManager;
import com.link.easyai.starter.engine.history.ChatHistoryManager;
import com.link.easyai.starter.engine.observability.PromptInjectionDetector;
import com.link.easyai.starter.engine.session.SessionManager;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.engine.state.TaskStateManager;
import com.link.easyai.starter.engine.state.TaskStatus;
import com.link.easyai.starter.engine.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 默认 AI 聊天服务实现。
 * <p>
 * 统一入口，自动处理：
 * <ol>
 *   <li>会话状态管理（加载/创建/超时判断）</li>
 *   <li>意图识别（LLM 优先，带上下文判断 continue/switch/cancel）</li>
 *   <li>任务切换（旧任务取消，新任务创建）</li>
 *   <li>任务执行（委托给 AiTaskEngine）</li>
 *   <li>输入安全（长度限制 + Prompt 注入检测）</li>
 * </ol>
 */
@Service
public class DefaultAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChatService.class);

    private final AiTaskEngine aiTaskEngine;
    private final IntentEngine intentEngine;
    private final SessionManager sessionManager;
    private final TaskStateManager taskStateManager;
    private final AiTaskProperties properties;
    private final SnowflakeIdGenerator idGenerator;
    private final TaskLockManager lockManager;
    private final ChatHistoryManager chatHistoryManager;

    @Autowired
    public DefaultAiChatService(AiTaskEngine aiTaskEngine,
                                 IntentEngine intentEngine,
                                 SessionManager sessionManager,
                                 TaskStateManager taskStateManager,
                                 AiTaskProperties properties,
                                 SnowflakeIdGenerator idGenerator,
                                 TaskLockManager lockManager,
                                 ChatHistoryManager chatHistoryManager) {
        this.aiTaskEngine = aiTaskEngine;
        this.intentEngine = intentEngine;
        this.sessionManager = sessionManager;
        this.taskStateManager = taskStateManager;
        this.properties = properties;
        this.idGenerator = idGenerator;
        this.lockManager = lockManager;
        this.chatHistoryManager = chatHistoryManager;
    }

    @Override
    public ChatResponse chat(String message, String sessionId, TaskContext context) {
        if (message == null || message.isBlank()) {
            return ChatResponse.fallback("请输入有效消息");
        }

        // 1. 输入安全检查
        String safeMessage = sanitizeInput(message);

        // 2. 分布式锁：防止同一 sessionId 并发请求导致状态错乱
        //    锁过期时间60秒（LLM调用通常2-10秒，留足余量）
        String lockKey = "easyai:session:" + sessionId;
        String lockToken = lockManager.tryLock(lockKey, 60);
        if (lockToken == null) {
            log.warn("[AiChatService] session={} is locked by another request, rejecting", sessionId);
            return ChatResponse.fallback("请求过于频繁，请稍后再试");
        }

        try {
            return doChat(safeMessage, sessionId, context);
        } finally {
            lockManager.unlock(lockKey, lockToken);
        }
    }

    private ChatResponse doChat(String safeMessage, String sessionId, TaskContext context) {
        // 3. 加载/创建会话
        Long tenantId = context != null && context.getTenantId() != null ? context.getTenantId() : 0L;
        AiChatSession session = sessionManager.loadOrCreate(sessionId, tenantId);

        // 3. 会话超时检查：超时后自动重置并把当前消息作为新会话的第一条消息处理
        int timeoutMinutes = properties.getLifecycle() != null
                ? properties.getLifecycle().getTimeoutMinutes() : 30;
        boolean sessionReset = false;
        if (sessionManager.isExpired(session, timeoutMinutes)) {
            // 取消旧任务（如果仍有绑定）
            if (session.getCurrentTaskId() != null && !session.getCurrentTaskId().isBlank()) {
                cancelTask(session.getCurrentTaskId());
            }
            // 重置会话为 IDLE，复用当前 sessionId，避免用户必须换 ID 才能继续
            sessionManager.reset(sessionId);
            // 超时重置时清空对话历史，开启全新会话
            chatHistoryManager.clearHistory(sessionId);
            sessionReset = true;
            log.info("[AiChatService] session={} expired ({}min idle), reset and continuing as new session",
                    sessionId, timeoutMinutes);
            // 同步清理内存对象，避免后续 hasActiveTask 判断误用旧值
            session.setCurrentTaskId(null);
            session.setCurrentTaskType(null);
            session.setStatus(AiChatSession.STATUS_IDLE);
        }

        // 4. 根据是否有活跃任务，走不同分支
        boolean hasActiveTask = session.getCurrentTaskId() != null && !session.getCurrentTaskId().isBlank()
                && session.getStatus() != null && session.getStatus() == AiChatSession.STATUS_ACTIVE;

        ChatResponse response;
        if (hasActiveTask) {
            response = handleWithActiveTask(safeMessage, session, context);
        } else if (!sessionReset) {
            // 无活跃任务且非超时重置：尝试恢复上一轮未完成任务（LLM 判断连续性）
            TaskState recoveredTask = tryRecoverLastTask(safeMessage, session);
            if (recoveredTask != null) {
                // 恢复成功：重新绑定 session 到该任务，继续执行
                sessionManager.bindTask(session.getSessionId(), recoveredTask.getTaskId(),
                        recoveredTask.getTaskType());
                response = executeExistingTask(safeMessage, session, recoveredTask.getTaskId(),
                        recoveredTask.getTaskType(), context);
            } else {
                response = handleNewTask(safeMessage, session, context);
            }
        } else {
            response = handleNewTask(safeMessage, session, context);
        }

        // 超时重置后的会话，在回复前附加友好提示
        if (sessionReset) {
            String prefix = "之前的会话已超时（超过 " + timeoutMinutes + " 分钟未活跃），已为您开启新会话。\n\n";
            response.setMessage(prefix + response.getMessage());
        }

        // 记录对话历史（滑动窗口），用于下一轮的上下文理解
        try {
            String taskId = response.getTaskId() != null ? response.getTaskId() : session.getCurrentTaskId();
            String taskType = response.getTaskType() != null ? response.getTaskType() : session.getCurrentTaskType();
            chatHistoryManager.appendUserMessage(sessionId, safeMessage, taskId, taskType, tenantId);
            chatHistoryManager.appendAssistantMessage(sessionId, response.getMessage(), taskId, taskType, tenantId);
        } catch (Exception e) {
            log.warn("[AiChatService] failed to record chat history for session={}: {}", sessionId, e.getMessage());
        }

        return response;
    }

    @Override
    public ChatResponse chatWithTaskType(String message, String sessionId, String taskType, TaskContext context) {
        if (message == null || message.isBlank() || taskType == null || taskType.isBlank()) {
            return ChatResponse.fallback("message 和 taskType 不能为空");
        }

        String safeMessage = sanitizeInput(message);

        // 分布式锁：防止同一 sessionId 并发请求
        String lockKey = "easyai:session:" + sessionId;
        String lockToken = lockManager.tryLock(lockKey, 60);
        if (lockToken == null) {
            log.warn("[AiChatService] chatWithTaskType session={} is locked, rejecting", sessionId);
            return ChatResponse.fallback("请求过于频繁，请稍后再试");
        }

        try {
            return doChatWithTaskType(safeMessage, sessionId, taskType, context);
        } finally {
            lockManager.unlock(lockKey, lockToken);
        }
    }

    private ChatResponse doChatWithTaskType(String safeMessage, String sessionId, String taskType, TaskContext context) {
        Long tenantId = context != null && context.getTenantId() != null ? context.getTenantId() : 0L;
        AiChatSession session = sessionManager.loadOrCreate(sessionId, tenantId);

        // 会话超时检查：与 chat() 保持一致，超时后自动重置再创建新任务
        int timeoutMinutes = properties.getLifecycle() != null
                ? properties.getLifecycle().getTimeoutMinutes() : 30;
        boolean sessionReset = false;
        if (sessionManager.isExpired(session, timeoutMinutes)) {
            if (session.getCurrentTaskId() != null && !session.getCurrentTaskId().isBlank()) {
                cancelTask(session.getCurrentTaskId());
            }
            sessionManager.reset(sessionId);
            chatHistoryManager.clearHistory(sessionId);
            sessionReset = true;
            log.info("[AiChatService] chatWithTaskType session={} expired, reset and continuing", sessionId);
            session.setCurrentTaskId(null);
            session.setCurrentTaskType(null);
            session.setStatus(AiChatSession.STATUS_IDLE);
        }

        // 直接创建/绑定任务
        ChatResponse response = createAndExecuteTask(safeMessage, session, taskType, context);

        if (sessionReset) {
            String prefix = "之前的会话已超时（超过 " + timeoutMinutes + " 分钟未活跃），已为您开启新会话。\n\n";
            response.setMessage(prefix + response.getMessage());
        }

        // 记录对话历史
        try {
            String taskId = response.getTaskId() != null ? response.getTaskId() : session.getCurrentTaskId();
            String respTaskType = response.getTaskType() != null ? response.getTaskType() : taskType;
            chatHistoryManager.appendUserMessage(sessionId, safeMessage, taskId, respTaskType, tenantId);
            chatHistoryManager.appendAssistantMessage(sessionId, response.getMessage(), taskId, respTaskType, tenantId);
        } catch (Exception e) {
            log.warn("[AiChatService] failed to record chat history (chatWithTaskType) session={}: {}", sessionId, e.getMessage());
        }

        return response;
    }

    // ---- 有活跃任务：判断 continue/switch/cancel ----

    private ChatResponse handleWithActiveTask(String message, AiChatSession session, TaskContext context) {
        String currentTaskId = session.getCurrentTaskId();
        String currentTaskType = session.getCurrentTaskType();

        // 构建已收集字段的简要描述（用于 LLM 上下文判断）
        String collectedFields = buildCollectedFieldsSummary(currentTaskId);

        // LLM 判断 continue/switch/cancel
        IntentResult result = intentEngine.recognizeWithContext(
                message, currentTaskType, currentTaskType, collectedFields);

        if (result.isCancel()) {
            // 取消当前任务
            cancelTask(currentTaskId);
            sessionManager.clearTask(session.getSessionId());
            log.info("[AiChatService] session={} cancelled task={}", session.getSessionId(), currentTaskId);
            return ChatResponse.fallback("好的，已取消当前任务。有什么可以帮您的？");
        }

        if (result.isSwitch() && result.getTaskType() != null
                && !result.getTaskType().equals(currentTaskType)) {
            // 切换到新任务
            log.info("[AiChatService] session={} switching from {} to {}",
                    session.getSessionId(), currentTaskType, result.getTaskType());
            cancelTask(currentTaskId);
            return createAndExecuteTask(message, session, result.getTaskType(), context);
        }

        // continue：继续当前任务
        return executeExistingTask(message, session, currentTaskId, currentTaskType, context);
    }

    // ---- 无活跃任务：全新意图识别 ----

    private ChatResponse handleNewTask(String message, AiChatSession session, TaskContext context) {
        IntentResult result = intentEngine.recognize(message);

        if (result.isNoMatch()) {
            // 无匹配，返回兜底 + 候选列表
            List<String> candidates = result.getCandidates();
            String hint = "抱歉，我不太确定您想做什么。";
            if (candidates != null && !candidates.isEmpty()) {
                hint += " 您可以尝试：" + String.join("、", candidates);
            }
            return ChatResponse.fallback(hint);
        }

        if (result.isAmbiguous()) {
            // 低置信度，返回澄清
            return ChatResponse.clarify(
                    "您是想进行以下哪个操作？",
                    result.getCandidates(),
                    result.getReason());
        }

        // 高置信度，创建任务
        return createAndExecuteTask(message, session, result.getTaskType(), context);
    }

    // ---- 创建并执行任务 ----

    private ChatResponse createAndExecuteTask(String message, AiChatSession session,
                                                String taskType, TaskContext context) {
        // 创建任务（通过 AiTaskEngine 的初始化机制）
        // 注意：AiTaskEngine.execute 内部会处理任务状态的创建/加载
        String taskId = generateTaskId(session.getSessionId(), taskType);

        // 绑定任务到会话
        sessionManager.bindTask(session.getSessionId(), taskId, taskType);

        // 执行任务
        return executeExistingTask(message, session, taskId, taskType, context);
    }

    // ---- 执行已有任务 ----

    private ChatResponse executeExistingTask(String message, AiChatSession session,
                                               String taskId, String taskType, TaskContext context) {
        // 更新会话活跃时间
        sessionManager.touch(session.getSessionId());

        // 构建 TaskContext
        TaskContext taskContext = context != null ? context : TaskContext.builder()
                .taskId(taskId)
                .taskType(taskType)
                .tenantId(session.getTenantId())
                .data(new LinkedHashMap<>())
                .build();
        if (taskContext.getTaskId() == null) {
            taskContext.setTaskId(taskId);
        }
        if (taskContext.getTaskType() == null) {
            taskContext.setTaskType(taskType);
        }
        // 设置 sessionId，用于引擎加载对话历史
        if (taskContext.getSessionId() == null) {
            taskContext.setSessionId(session.getSessionId());
        }

        // 执行任务管道
        AiTaskResponse engineResponse = aiTaskEngine.execute(taskType, taskId, message, taskContext);

        // 任务完成时清除会话绑定
        if (engineResponse.isCompleted()) {
            sessionManager.clearTask(session.getSessionId());
            log.info("[AiChatService] session={} task={} completed, session cleared",
                    session.getSessionId(), taskId);
        }

        return mapToChatResponse(engineResponse, taskType);
    }

    // ---- 辅助方法 ----

    private String sanitizeInput(String message) {
        // 长度限制
        int maxLength = properties.getLlm() != null && properties.getLlm().getMaxInputLength() > 0
                ? properties.getLlm().getMaxInputLength() : 2000;
        if (message.length() > maxLength) {
            log.warn("[AiChatService] input truncated from {} to {} chars", message.length(), maxLength);
            message = message.substring(0, maxLength);
        }

        // Prompt 注入检测（仅记录告警，不阻断，因为提取引擎已做角色隔离）
        if (PromptInjectionDetector.isInjectionDetected(message)) {
            log.warn("[AiChatService] potential prompt injection detected in user message");
        }

        return message;
    }

    private void cancelTask(String taskId) {
        try {
            TaskState state = taskStateManager.load(taskId, null, null);
            if (state != null && state.getTaskId() != null) {
                state.setStatus(TaskStatus.CANCELLED);
                taskStateManager.save(state);
                log.info("[AiChatService] task={} marked CANCELLED", taskId);
            }
        } catch (Exception e) {
            log.warn("[AiChatService] failed to cancel task={}: {}", taskId, e.getMessage());
        }
    }

    private String buildCollectedFieldsSummary(String taskId) {
        try {
            TaskState state = taskStateManager.load(taskId, null, null);
            if (state == null || state.getFields() == null || state.getFields().isEmpty()) {
                return "无";
            }
            StringBuilder sb = new StringBuilder();
            state.getFields().forEach((code, fieldState) -> {
                if (fieldState != null && fieldState.isCompleted()) {
                    if (sb.length() > 0) sb.append(", ");
                    Object val = fieldState.getDisplayValue() != null
                            ? fieldState.getDisplayValue()
                            : (fieldState.getRawValue() != null ? fieldState.getRawValue() : fieldState.getValue());
                    sb.append(code).append("=").append(String.valueOf(val));
                }
            });
            return sb.length() > 0 ? sb.toString() : "无";
        } catch (Exception e) {
            return "无";
        }
    }

    private String generateTaskId(String sessionId, String taskType) {
        // 雪花算法生成全局唯一、趋势递增的任务ID
        // 替代原有的"时间戳+随机数"方案，消除高并发下的ID碰撞风险
        return idGenerator.nextIdString();
    }

    /**
     * 尝试恢复上一轮未完成的任务。
     * <p>
     * 当 session 中没有活跃任务时调用：从数据库查找最近一个未完成任务，
     * 通过 LLM 判断当前用户消息是否与该任务连续。如果连续，返回任务状态
     * 供调用方重新绑定并继续执行；否则返回 null，开启新任务。
     *
     * @param message 当前用户消息（已安全处理）
     * @param session 当前会话
     * @return 可恢复的任务状态，或 null
     */
    private TaskState tryRecoverLastTask(String message, AiChatSession session) {
        Long tenantId = session.getTenantId() != null ? session.getTenantId() : 0L;
        TaskState lastTask = taskStateManager.findLatestActiveTask(tenantId);
        if (lastTask == null) {
            return null;
        }

        // 状态校验：只恢复 COLLECTING / READY / EXECUTING 状态的任务
        TaskStatus status = lastTask.getStatus();
        if (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED
                || status == TaskStatus.EXPIRED || status == TaskStatus.FAILED
                || status == TaskStatus.INITIALIZED || status == null) {
            log.debug("[AiChatService] last task {} has non-recoverable status={}", lastTask.getTaskId(), status);
            return null;
        }

        // 构建已收集字段摘要，供 LLM 判断上下文
        String collectedFields = buildCollectedFieldsSummary(lastTask.getTaskId());

        // LLM 连续性判断
        boolean continuous = intentEngine.judgeContinuity(message, lastTask.getTaskType(),
                lastTask.getTaskType(), collectedFields, null);
        if (continuous) {
            log.info("[AiChatService] recovered last task: session={}, taskId={}, taskType={}, status={}",
                    session.getSessionId(), lastTask.getTaskId(), lastTask.getTaskType(), status);
            return lastTask;
        }
        log.info("[AiChatService] last task {} not continuous with current message, starting new task",
                lastTask.getTaskId());
        return null;
    }

    private ChatResponse mapToChatResponse(AiTaskResponse engineResponse, String taskType) {
        if (engineResponse.isCompleted()) {
            return ChatResponse.done(
                    engineResponse.getTaskId(),
                    engineResponse.getMessage(),
                    engineResponse.getActionResult(),
                    engineResponse.getState()
            ).withTaskType(taskType);
        }

        return ChatResponse.needMore(
                engineResponse.getTaskId(),
                engineResponse.getMessage(),
                engineResponse.getState()
        ).withTaskType(taskType);
    }
}
