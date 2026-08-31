package com.link.easyai.starter.engine.state;

import com.link.easyai.starter.engine.AiTaskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务过期定时清理器。
 * <p>
 * 后台定时扫描处理中（status=2）但已超过 {@code timeout-minutes} 未更新的任务，
 * 主动将其标记为 {@link TaskStatus#EXPIRED}，避免陈旧任务被
 * {@link TaskStateManager#findLatestActiveTask} 在很久之后错误恢复。
 * <p>
 * 配置：
 * <pre>
 * easy-ai:
 *   task-engine:
 *     lifecycle:
 *       timeout-minutes: 30          # 超时阈值（分钟）
 *       expire-enabled: true          # 是否启用定时过期（默认 true）
 *       expire-interval-ms: 600000    # 检查间隔（毫秒，默认 10 分钟）
 * </pre>
 */
@Component
public class TaskExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskExpirationScheduler.class);

    private final TaskStateManager taskStateManager;
    private final AiTaskProperties properties;

    public TaskExpirationScheduler(TaskStateManager taskStateManager,
                                   AiTaskProperties properties) {
        this.taskStateManager = taskStateManager;
        this.properties = properties;
    }

    /**
     * 定时执行：将超时未更新的处理中任务标记为 EXPIRED。
     * 使用 fixedDelay，上一次执行完成后间隔 expire-interval-ms 再执行。
     */
    @Scheduled(fixedDelayString = "${easy-ai.task-engine.lifecycle.expire-interval-ms:600000}")
    public void expireIdleTasks() {
        if (properties.getLifecycle() == null || !properties.getLifecycle().isExpireEnabled()) {
            return;
        }
        int timeoutMinutes = properties.getLifecycle().getTimeoutMinutes();
        try {
            int marked = taskStateManager.markExpiredTasks(timeoutMinutes);
            if (marked > 0) {
                log.info("[TaskExpirationScheduler] expired {} idle task(s) (timeout={}min)",
                        marked, timeoutMinutes);
            }
        } catch (Exception e) {
            log.warn("[TaskExpirationScheduler] failed to expire idle tasks: {}", e.getMessage());
        }
    }
}
