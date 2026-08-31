package com.link.easyai.starter.engine.state;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.TbChatSessionTask;
import com.link.easyai.starter.mapper.AiChatMessageMapper;
import com.link.easyai.starter.mapper.TbChatSessionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Database-backed implementation of {@link TaskStateManager}.
 * <p>
 * Persists the full {@link TaskState} as JSON in the <code>ai_task_state</code>
 * column of <code>ai_chat_session_task</code>. This ensures every turn of a
 * conversation can restore the complete state from the database, even if the
 * JVM restarts.
 * <p>
 * The <code>taskId</code> used by the engine corresponds to the
 * <code>id</code> (Long) of <code>ai_chat_session_task</code> serialized as a
 * String. When <code>taskId</code> is null or blank, a fresh in-memory state is
 * created; the record is only inserted on the first {@link #save} call.
 * <p>
 * <b>Optimistic locking:</b> each save uses {@code version} for CAS. On conflict
 * the latest state is reloaded, field-level merged (later write wins per field),
 * and retried up to {@link #MAX_RETRIES} times.
 */
@Component
public class DefaultTaskStateManager implements TaskStateManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultTaskStateManager.class);

    /** Maximum retry attempts on optimistic lock conflict. */
    private static final int MAX_RETRIES = 3;

    private final TbChatSessionTaskMapper taskMapper;
    private final AiChatMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public DefaultTaskStateManager(TbChatSessionTaskMapper taskMapper,
                                   AiChatMessageMapper messageMapper,
                                   ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskState load(String taskId, String taskType, Integer configVersion) {
        if (taskId == null || taskId.isBlank()) {
            log.debug("[TaskStateManager] taskId is null/blank, creating fresh state: taskType={}", taskType);
            return create(taskId, taskType, configVersion);
        }

        // 按业务键 task_id 查询（支持任意字符串格式的 taskId）
        TbChatSessionTask entity = taskMapper.selectByTaskId(taskId);
        if (entity == null) {
            log.debug("[TaskStateManager] no task record found for taskId={}, creating fresh state", taskId);
            return create(taskId, taskType, configVersion);
        }

        // Try to deserialize the persisted state JSON
        String stateJson = entity.getAiTaskState();
        if (stateJson == null || stateJson.isBlank()) {
            log.debug("[TaskStateManager] no state JSON for taskId={}, creating fresh state", taskId);
            return create(taskId, taskType,
                    configVersion != null ? configVersion : entity.getConfigVersion());
        }

        try {
            TaskState state = objectMapper.readValue(stateJson, TaskState.class);
            // Ensure taskId is always set (it might be a String in the JSON)
            if (state.getTaskId() == null) {
                state.setTaskId(taskId);
            }
            // Sync version from DB entity (source of truth for optimistic lock)
            if (entity.getVersion() != null) {
                state.setVersion(entity.getVersion());
            }
            log.debug("[TaskStateManager] loaded state: taskId={}, status={}, fields={}, version={}",
                    state.getTaskId(), state.getStatus(), state.getFields().size(), state.getVersion());
            return state;
        } catch (Exception e) {
            log.error("[TaskStateManager] failed to deserialize state JSON for taskId={}: {}", taskId, e.getMessage(), e);
            return create(taskId, taskType, configVersion);
        }
    }

    @Override
    public void save(TaskState state) {
        if (state == null || state.getTaskId() == null || state.getTaskId().isBlank()) {
            log.warn("[TaskStateManager] cannot save state: taskId is null or blank");
            return;
        }

        try {
            String stateJson = objectMapper.writeValueAsString(state);

            // 按业务键 task_id 查询（支持任意字符串格式的 taskId）
            TbChatSessionTask entity = taskMapper.selectByTaskId(state.getTaskId());
            if (entity == null) {
                // Record doesn't exist — create a new one (id 自增，task_id 为业务键)
                entity = new TbChatSessionTask();
                entity.setTaskId(state.getTaskId());
                entity.setTaskType(state.getTaskType());
                entity.setConfigVersion(state.getConfigVersion());
                entity.setAiTaskState(stateJson);
                entity.setStatus(mapStatusToInt(state.getStatus()));
                entity.setType(1); // AI-driven task type
                entity.setVersion(0);
                // 意图识别信息（仅在任务创建时记录，后续轮次不覆盖）
                entity.setIntentReason(state.getIntentReason());
                entity.setIntentConfidence(state.getIntentConfidence());
                entity.setIntentSource(state.getIntentSource());
                // tenant_id：优先从 state.context 取，兜底 "0"（数据库列无默认值，必须显式设置）
                String tenantId = state.getFromContext("tenantId");
                entity.setTenantId(tenantId != null && !tenantId.isBlank() ? tenantId : "0");
                // 审计字段（数据库 NOT NULL，必须显式设置，不依赖自动填充）
                Date now = new Date();
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                entity.setCreateBy(0L);
                entity.setUpdateBy(0L);
                entity.setDeleted(0);
                taskMapper.insert(entity);
                state.setVersion(0);
                log.info("[TaskStateManager] created new task record: taskId={}, id={}, taskType={}",
                        state.getTaskId(), entity.getId(), state.getTaskType());
                return;
            }

            // Update existing record with optimistic lock
            saveWithRetry(state, entity, stateJson, MAX_RETRIES);

        } catch (Exception e) {
            log.error("[TaskStateManager] failed to save state for taskId={}: {}", state.getTaskId(), e.getMessage(), e);
            throw new RuntimeException("Failed to persist task state", e);
        }
    }

    /**
     * Save with optimistic lock retry. On conflict, reload latest state,
     * merge field-level (our write wins per field), and retry.
     */
    private void saveWithRetry(TaskState state, TbChatSessionTask entity,
                                String stateJson, int remainingRetries) {
        Long id = entity.getId();
        Integer expectedVersion = state.getVersion() != null ? state.getVersion() : 0;

        // Build update entity
        TbChatSessionTask updateEntity = new TbChatSessionTask();
        updateEntity.setId(id);
        updateEntity.setAiTaskState(stateJson);
        updateEntity.setTaskType(state.getTaskType());
        updateEntity.setConfigVersion(state.getConfigVersion());
        updateEntity.setStatus(mapStatusToInt(state.getStatus()));
        // Preserve existing fields that the engine doesn't manage
        updateEntity.setType(entity.getType());
        updateEntity.setFieldList(entity.getFieldList());
        updateEntity.setRecords(entity.getRecords());
        updateEntity.setExtraContent(entity.getExtraContent());
        updateEntity.setScenarioCode(entity.getScenarioCode());
        updateEntity.setTenantId(entity.getTenantId());
        // 意图识别信息：保留创建时的原始值，不随后续轮次更新覆盖
        updateEntity.setIntentReason(entity.getIntentReason());
        updateEntity.setIntentConfidence(entity.getIntentConfidence());
        updateEntity.setIntentSource(entity.getIntentSource());

        int rows = taskMapper.updateWithVersion(updateEntity, expectedVersion);
        if (rows > 0) {
            // Success — increment local version to match DB
            state.setVersion(expectedVersion + 1);
            log.debug("[TaskStateManager] updated task record: id={}, status={}, version={}",
                    id, state.getStatus(), state.getVersion());
            return;
        }

        // Concurrent conflict — reload and merge
        if (remainingRetries <= 0) {
            log.error("[TaskStateManager] optimistic lock conflict exhausted retries for id={}", id);
            throw new RuntimeException("操作过于频繁，请稍后重试");
        }

        log.warn("[TaskStateManager] optimistic lock conflict for id={}, expectedVersion={}, retrying ({} left)",
                id, expectedVersion, remainingRetries - 1);

        // Reload latest state from DB
        TbChatSessionTask latestEntity = taskMapper.selectById(id);
        if (latestEntity == null || latestEntity.getAiTaskState() == null) {
            // Record disappeared — treat as fresh insert
            taskMapper.insert(updateEntity);
            state.setVersion(0);
            return;
        }

        try {
            TaskState latestState = objectMapper.readValue(latestEntity.getAiTaskState(), TaskState.class);
            // Field-level merge: our state's fields override latest (last write wins)
            mergeState(latestState, state);
            // Use latest version for retry
            latestState.setVersion(latestEntity.getVersion());
            // Re-serialize merged state
            String mergedJson = objectMapper.writeValueAsString(latestState);
            // Copy merged state back to original reference so caller sees merged result
            copyState(latestState, state);
            saveWithRetry(state, latestEntity, mergedJson, remainingRetries - 1);
        } catch (Exception e) {
            log.error("[TaskStateManager] failed to merge conflicting state for id={}: {}", id, e.getMessage(), e);
            throw new RuntimeException("Failed to persist task state after conflict", e);
        }
    }

    /**
     * Field-level merge: source (our write) overrides target (latest from DB)
     * for each field that exists in source. Status and context from source win.
     */
    private void mergeState(TaskState target, TaskState source) {
        if (source.getStatus() != null) {
            target.setStatus(source.getStatus());
        }
        if (source.getContext() != null) {
            target.setContext(source.getContext());
        }
        if (source.getFields() != null) {
            for (Map.Entry<String, FieldState> entry : source.getFields().entrySet()) {
                target.putField(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Copy all fields from source to target (in-place update so caller's
     * reference reflects the merged result).
     */
    private void copyState(TaskState source, TaskState target) {
        target.setTaskId(source.getTaskId());
        target.setTaskType(source.getTaskType());
        target.setConfigVersion(source.getConfigVersion());
        target.setStatus(source.getStatus());
        target.setFields(source.getFields());
        target.setContext(source.getContext());
        target.setVersion(source.getVersion());
    }

    /**
     * Parse a String taskId into a Long database id.
     *
     * @param taskId the String task ID
     * @return the Long id, or null if parsing fails
     */
    private Long parseTaskId(String taskId) {
        try {
            return Long.parseLong(taskId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Map the engine's {@link TaskStatus} to the int status column used by
     * ai_chat_session_task.
     * <p>
     * Existing status convention: 0-待处理 1-待唤醒 2-处理中 3-失败 4-已停止 5-已完成
     */
    private int mapStatusToInt(TaskStatus status) {
        if (status == null) return 0;
        switch (status) {
            case INITIALIZED: return 0;
            case COLLECTING:
            case READY:
            case EXECUTING:
                return 2;
            case COMPLETED: return 5;
            case FAILED: return 3;
            case CANCELLED:
            case EXPIRED:
                return 4;
            default: return 0;
        }
    }

    @Override
    public TaskState findLatestActiveTask(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "0";
        }
        TbChatSessionTask entity = taskMapper.selectLatestActiveByTenant(tenantId);
        if (entity == null) {
            log.debug("[TaskStateManager] no active task found for tenantId={}", tenantId);
            return null;
        }
        String stateJson = entity.getAiTaskState();
        if (stateJson == null || stateJson.isBlank()) {
            log.debug("[TaskStateManager] latest active task id={} has no state JSON", entity.getId());
            return null;
        }
        try {
            TaskState state = objectMapper.readValue(stateJson, TaskState.class);
            // 确保 taskId 与业务键一致（优先用 entity.taskId，兜底用 id）
            if (state.getTaskId() == null || state.getTaskId().isBlank()) {
                state.setTaskId(entity.getTaskId() != null ? entity.getTaskId() : String.valueOf(entity.getId()));
            }
            // 同步版本号
            if (entity.getVersion() != null) {
                state.setVersion(entity.getVersion());
            }
            log.info("[TaskStateManager] found latest active task: id={}, taskType={}, status={}, fields={}",
                    entity.getId(), state.getTaskType(), state.getStatus(),
                    state.getFields() != null ? state.getFields().size() : 0);
            return state;
        } catch (Exception e) {
            log.warn("[TaskStateManager] failed to deserialize latest active task state id={}: {}",
                    entity.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    public TaskState findLatestActiveTaskBySession(String sessionId, String tenantId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        // 通过消息记录反查该会话最近关联的任务（按租户+会话复合维度，避免跨租户串任务）
        String expectTenant = tenantId != null && !tenantId.isBlank() ? tenantId : "0";
        String latestTaskId = messageMapper.selectLatestTaskIdBySession(sessionId, expectTenant);
        if (latestTaskId == null || latestTaskId.isBlank()) {
            log.debug("[TaskStateManager] no task associated with session={}, tenant={}, skip recovery",
                    sessionId, expectTenant);
            return null;
        }

        // 按业务键加载任务
        TbChatSessionTask entity = taskMapper.selectByTaskId(latestTaskId);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            log.debug("[TaskStateManager] session={} latest task={} not found or deleted", sessionId, latestTaskId);
            return null;
        }

        // 租户二次校验：任务必须属于当前会话的租户，防止跨租户串任务
        String taskTenant = entity.getTenantId() != null ? entity.getTenantId() : "0";
        if (!taskTenant.equals(expectTenant)) {
            log.warn("[TaskStateManager] session={} latest task={} tenant={} != expected={}, skip recovery",
                    sessionId, latestTaskId, taskTenant, expectTenant);
            return null;
        }

        // 状态校验：仅处理中任务可恢复
        if (entity.getStatus() == null || entity.getStatus() != 2) {
            log.debug("[TaskStateManager] session={} latest task={} status={}, not recoverable",
                    sessionId, latestTaskId, entity.getStatus());
            return null;
        }

        // 反序列化完整状态
        String stateJson = entity.getAiTaskState();
        if (stateJson == null || stateJson.isBlank()) {
            log.debug("[TaskStateManager] session={} latest task={} has no state JSON", sessionId, latestTaskId);
            return null;
        }
        try {
            TaskState state = objectMapper.readValue(stateJson, TaskState.class);
            if (state.getTaskId() == null || state.getTaskId().isBlank()) {
                state.setTaskId(entity.getTaskId() != null ? entity.getTaskId() : String.valueOf(entity.getId()));
            }
            if (entity.getVersion() != null) {
                state.setVersion(entity.getVersion());
            }
            log.info("[TaskStateManager] found latest active task by session={}: taskId={}, taskType={}, status={}",
                    sessionId, state.getTaskId(), state.getTaskType(), state.getStatus());
            return state;
        } catch (Exception e) {
            log.warn("[TaskStateManager] failed to deserialize session={} latest task state id={}: {}",
                    sessionId, entity.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    public int markExpiredTasks(int timeoutMinutes) {
        if (timeoutMinutes <= 0) {
            timeoutMinutes = 30;
        }
        List<TbChatSessionTask> expiredTasks = taskMapper.selectExpiredActiveTasks(timeoutMinutes);
        if (expiredTasks == null || expiredTasks.isEmpty()) {
            return 0;
        }

        int marked = 0;
        for (TbChatSessionTask entity : expiredTasks) {
            try {
                // 加载状态并标记为 EXPIRED（save 会走乐观锁 + 更新 status 列与 state JSON）
                TaskState state = load(entity.getTaskId(), entity.getTaskType(), entity.getConfigVersion());
                if (state == null || state.getTaskId() == null) {
                    continue;
                }
                // 二次确认：仅处理仍是处理中的任务，避免并发下把刚恢复的任务误标过期
                TaskStatus currentStatus = state.getStatus();
                if (currentStatus != TaskStatus.COLLECTING
                        && currentStatus != TaskStatus.READY
                        && currentStatus != TaskStatus.EXECUTING
                        && currentStatus != TaskStatus.INITIALIZED) {
                    continue;
                }
                state.setStatus(TaskStatus.EXPIRED);
                save(state);
                marked++;
                log.info("[TaskStateManager] marked task={} ({}), taskType={} as EXPIRED, idle since update_time={}",
                        entity.getTaskId(), entity.getId(), entity.getTaskType(), entity.getUpdateTime());
            } catch (Exception e) {
                log.warn("[TaskStateManager] failed to expire task={}: {}", entity.getTaskId(), e.getMessage());
            }
        }
        log.info("[TaskStateManager] markExpiredTasks finished: {} task(s) expired, {} processed",
                marked, expiredTasks.size());
        return marked;
    }
}
