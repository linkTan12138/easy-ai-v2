package com.link.easyai.starter.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 分布式锁 Mapper。
 * <p>
 * 基于数据库唯一键 + 过期时间实现的轻量级分布式锁，
 * 不依赖 Redis 等额外中间件，适合已有数据库的场景。
 */
@Mapper
public interface AiTaskLockMapper {

    /**
     * 尝试插入锁记录（获取锁）。
     * 唯一键冲突表示锁已被持有，插入失败。
     */
    @Insert("INSERT INTO ai_task_lock (lock_key, lock_owner, lock_time, expire_time) " +
            "VALUES (#{lockKey}, #{lockOwner}, NOW(), DATE_ADD(NOW(), INTERVAL #{expireSeconds} SECOND))")
    int insertLock(@Param("lockKey") String lockKey,
                    @Param("lockOwner") String lockOwner,
                    @Param("expireSeconds") int expireSeconds);

    /**
     * 尝试抢占已过期的锁（CAS：只有当锁已过期时才能覆盖）。
     */
    @Update("UPDATE ai_task_lock SET lock_owner = #{lockOwner}, lock_time = NOW(), " +
            "expire_time = DATE_ADD(NOW(), INTERVAL #{expireSeconds} SECOND) " +
            "WHERE lock_key = #{lockKey} AND expire_time < NOW()")
    int reclaimExpiredLock(@Param("lockKey") String lockKey,
                            @Param("lockOwner") String lockOwner,
                            @Param("expireSeconds") int expireSeconds);

    /**
     * 释放锁（只有持有者本人才能释放）。
     */
    @Delete("DELETE FROM ai_task_lock WHERE lock_key = #{lockKey} AND lock_owner = #{lockOwner}")
    int releaseLock(@Param("lockKey") String lockKey,
                     @Param("lockOwner") String lockOwner);

    /**
     * 查询锁是否被持有（未过期）。
     */
    @Select("SELECT COUNT(*) FROM ai_task_lock WHERE lock_key = #{lockKey} AND expire_time > NOW()")
    int countActiveLock(@Param("lockKey") String lockKey);

    /**
     * 清理所有已过期的锁记录（定时任务调用）。
     */
    @Delete("DELETE FROM ai_task_lock WHERE expire_time < NOW()")
    int cleanExpiredLocks();
}
