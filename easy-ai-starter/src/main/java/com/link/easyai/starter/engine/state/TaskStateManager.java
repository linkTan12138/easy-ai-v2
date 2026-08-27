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
     * @param tenantId 租户 ID
     * @return 最近未完成任务的状态，或 null
     */
    TaskState findLatestActiveTask(Long tenantId);
}
