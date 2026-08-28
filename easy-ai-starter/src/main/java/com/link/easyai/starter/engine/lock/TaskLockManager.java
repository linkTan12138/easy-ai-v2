package com.link.easyai.starter.engine.lock;

/**
 * 任务分布式锁管理器。
 * <p>
 * 防止同一 sessionId / taskId 的并发请求重复执行 LLM 调用和 Action，
 * 导致状态错乱、重复扣费、重复执行业务操作等问题。
 * <p>
 * 使用方式：
 * <pre>
 * String lockToken = lockManager.tryLock("session:" + sessionId, 30);
 * if (lockToken == null) {
 *     throw new RuntimeException("请求过于频繁，请稍后重试");
 * }
 * try {
 *     // 执行业务逻辑
 * } finally {
 *     lockManager.unlock("session:" + sessionId, lockToken);
 * }
 * </pre>
 */
public interface TaskLockManager {

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey     锁的唯一标识（如 "session:xxx" 或 "task:xxx"）
     * @param expireSeconds 锁的过期时间（秒），防止持有者崩溃导致死锁
     * @return 锁令牌（释放锁时需要验证），获取失败返回 null
     */
    String tryLock(String lockKey, int expireSeconds);

    /**
     * 释放分布式锁。
     * <p>
     * 只有持有正确 lockToken 的调用者才能释放锁，防止误释放他人的锁。
     *
     * @param lockKey  锁的唯一标识
     * @param lockToken 获取锁时返回的令牌
     * @return true 表示释放成功，false 表示锁不存在或令牌不匹配
     */
    boolean unlock(String lockKey, String lockToken);

    /**
     * 检查锁是否被持有（不获取锁，仅查询状态）。
     *
     * @param lockKey 锁的唯一标识
     * @return true 表示锁当前被持有
     */
    boolean isLocked(String lockKey);
}
