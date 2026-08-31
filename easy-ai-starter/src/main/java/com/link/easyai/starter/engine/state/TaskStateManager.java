package com.link.easyai.starter.engine.state;

/**
 * Manages task state persistence and restoration.
 * <p>
 * Implementations are responsible for:
 * - Loading state from the database (or any persistent store)
 * - Saving state back to the database after each turn
 * - Creating a new state when a task starts
 * <p>
 * This interface ensures all state is recoverable across conversation turns.
 */
public interface TaskStateManager {

    /**
     * Load the task state for the given task ID.
     * If no state exists yet, returns a new initialized state.
     *
     * @param taskId   the task ID
     * @param taskType the task type
     * @param configVersion the config version to bind
     * @return the loaded or newly created TaskState
     */
    TaskState load(String taskId, String taskType, Integer configVersion);

    /**
     * Persist the task state.
     *
     * @param state the state to save
     */
    void save(TaskState state);

    /**
     * Create a fresh task state for a new task.
     *
     * @param taskId        the task ID
     * @param taskType       the task type
     * @param configVersion  the config version
     * @return a new TaskState with status=INITIALIZED and empty fields
     */
    default TaskState create(String taskId, String taskType, Integer configVersion) {
        return TaskState.builder()
                .taskId(taskId)
                .taskType(taskType)
                .configVersion(configVersion)
                .status(TaskStatus.INITIALIZED)
                .build();
    }

    /**
     * 查找指定租户最近一个未完成的任务（状态=处理中），用于多轮对话连续性恢复。
     * <p>
     * 当 session 中没有活跃任务时，调用此方法尝试找回上一轮未完成的任务。
     * 反序列化 ai_task_state JSON 返回完整的 TaskState；如果没有未完成任务或
     * 反序列化失败，返回 null。
     *
     * @param tenantId 租户 ID（支持数字或字符串编码）
     * @return 最近未完成任务的状态，或 null
     */
    TaskState findLatestActiveTask(String tenantId);

    /**
     * 按会话查找最近一个未完成的任务（状态=处理中），用于多租户多用户场景下的连续性恢复。
     * <p>
     * 与 {@link #findLatestActiveTask(Long)} 按租户维度查找不同，本方法通过
     * 会话的消息记录反查该会话最近关联的任务，再校验其仍处于处理中状态，
     * 从而保证「谁的会话，就恢复谁的任务」，避免同租户下不同用户串任务。
     *
     * @param sessionId 会话 ID
     * @param tenantId  期望的租户 ID（用于二次校验，可为 null）
     * @return 该会话最近未完成任务的状态，或 null
     */
    default TaskState findLatestActiveTaskBySession(String sessionId, String tenantId) {
        return null;
    }

    /**
     * 将超过指定分钟数未更新的处理中任务标记为 EXPIRED。
     * <p>
     * 供后台定时任务调用，主动清理长期未活跃的任务，避免其被
     * {@link #findLatestActiveTask} 在很久之后错误恢复。
     *
     * @param timeoutMinutes 超时阈值（分钟），update_time 距当前时间超过该值则视为超时
     * @return 被标记为 EXPIRED 的任务数量
     */
    default int markExpiredTasks(int timeoutMinutes) {
        return 0;
    }
}
