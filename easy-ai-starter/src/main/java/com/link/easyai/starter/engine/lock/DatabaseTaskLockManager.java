package com.link.easyai.starter.engine.lock;

import com.link.easyai.starter.mapper.AiTaskLockMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 基于数据库的分布式锁实现。
 * <p>
 * 利用数据库唯一键约束实现互斥，利用过期时间防止死锁。
 * 不依赖 Redis 等额外中间件，适合已有数据库的 Spring Boot 项目。
 * <p>
 * 获取锁流程：
 * <ol>
 *   <li>生成唯一 owner token（UUID）</li>
 *   <li>尝试 INSERT 锁记录，成功则获得锁</li>
 *   <li>INSERT 失败（唯一键冲突）说明锁已被持有，检查是否过期</li>
 *   <li>如果过期，尝试 UPDATE 抢占（CAS，只有过期的锁才能被覆盖）</li>
 *   <li>抢占成功则获得锁，否则返回 null</li>
 * </ol>
 * 释放锁：DELETE 记录（WHERE lock_key AND lock_owner，只有持有者能释放）。
 */
@Component
public class DatabaseTaskLockManager implements TaskLockManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTaskLockManager.class);

    private final AiTaskLockMapper lockMapper;

    @Autowired
    public DatabaseTaskLockManager(AiTaskLockMapper lockMapper) {
        this.lockMapper = lockMapper;
    }

    @Override
    public String tryLock(String lockKey, int expireSeconds) {
        if (lockKey == null || lockKey.isBlank()) {
            throw new IllegalArgumentException("lockKey cannot be blank");
        }
        if (expireSeconds <= 0) {
            expireSeconds = 30;
        }

        String owner = UUID.randomUUID().toString().replace("-", "");

        // 1. 尝试直接插入（锁未被持有时成功）
        try {
            int rows = lockMapper.insertLock(lockKey, owner, expireSeconds);
            if (rows > 0) {
                log.debug("[TaskLock] acquired lock: key={}, owner={}", lockKey, owner);
                return owner;
            }
        } catch (Exception e) {
            // 唯一键冲突或其他异常，继续尝试抢占过期锁
            log.debug("[TaskLock] insert lock failed (likely held): key={}, error={}", lockKey, e.getMessage());
        }

        // 2. 锁已被持有，尝试抢占已过期的锁
        try {
            int rows = lockMapper.reclaimExpiredLock(lockKey, owner, expireSeconds);
            if (rows > 0) {
                log.info("[TaskLock] reclaimed expired lock: key={}, owner={}", lockKey, owner);
                return owner;
            }
        } catch (Exception e) {
            log.warn("[TaskLock] reclaim expired lock failed: key={}, error={}", lockKey, e.getMessage());
        }

        log.debug("[TaskLock] lock is held by another owner, acquisition failed: key={}", lockKey);
        return null;
    }

    @Override
    public boolean unlock(String lockKey, String lockToken) {
        if (lockKey == null || lockKey.isBlank() || lockToken == null) {
            return false;
        }
        try {
            int rows = lockMapper.releaseLock(lockKey, lockToken);
            if (rows > 0) {
                log.debug("[TaskLock] released lock: key={}, owner={}", lockKey, lockToken);
                return true;
            } else {
                log.debug("[TaskLock] lock already released or owner mismatch: key={}", lockKey);
                return false;
            }
        } catch (Exception e) {
            log.warn("[TaskLock] release lock failed: key={}, error={}", lockKey, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }
        try {
            return lockMapper.countActiveLock(lockKey) > 0;
        } catch (Exception e) {
            log.warn("[TaskLock] check lock status failed: key={}, error={}", lockKey, e.getMessage());
            return false;
        }
    }
}
