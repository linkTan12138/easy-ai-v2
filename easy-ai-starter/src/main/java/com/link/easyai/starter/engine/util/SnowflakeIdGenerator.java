package com.link.easyai.starter.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 雪花算法 ID 生成器。
 * <p>
 * 64位ID结构：
 * <pre>
 * 0 - 41位时间戳(毫秒) - 5位数据中心ID - 5位工作机器ID - 12位序列号
 * </pre>
 * <ul>
 *   <li>符号位：1位，始终为0（正数）</li>
 *   <li>时间戳：41位，以起始时间为基准，可用约69年</li>
 *   <li>数据中心ID：5位，最多32个数据中心</li>
 *   <li>工作机器ID：5位，每个数据中心最多32台机器</li>
 *   <li>序列号：12位，同一毫秒内最多4096个ID</li>
 * </ul>
 * 相比 {@code 时间戳+随机数} 方案，雪花算法保证全局唯一、趋势递增、无碰撞，
 * 且性能更高（纯内存计算，无需随机数生成）。
 */
public class SnowflakeIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    /** 起始时间戳：2024-01-01 00:00:00 UTC */
    private static final long EPOCH = 1704067200000L;

    /** 工作机器ID位数 */
    private static final long WORKER_ID_BITS = 5L;
    /** 数据中心ID位数 */
    private static final long DATACENTER_ID_BITS = 5L;
    /** 序列号位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 工作机器ID最大值：31 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    /** 数据中心ID最大值：31 */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 工作机器ID偏移量：12位（序列号） */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    /** 数据中心ID偏移量：12+5=17位 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 时间戳偏移量：12+5+5=22位 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 序列号掩码：4095 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    /**
     * 创建雪花算法ID生成器。
     *
     * @param workerId     工作机器ID (0-31)
     * @param datacenterId 数据中心ID (0-31)
     */
    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                    String.format("workerId 不能大于 %d 或小于 0", MAX_WORKER_ID));
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                    String.format("datacenterId 不能大于 %d 或小于 0", MAX_DATACENTER_ID));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        log.info("[SnowflakeIdGenerator] initialized: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 生成下一个全局唯一ID。
     *
     * @return 雪花算法ID（long类型，转为String使用）
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset <= 5) {
                // 回拨在5ms内，等待追上
                try {
                    wait(offset << 1);
                    timestamp = timeGen();
                    if (timestamp < lastTimestamp) {
                        throw new RuntimeException(
                                String.format("时钟回拨，拒绝生成ID。回拨时长: %dms", lastTimestamp - timestamp));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("时钟回拨等待被中断", e);
                }
            } else {
                throw new RuntimeException(
                        String.format("时钟回拨超过阈值，拒绝生成ID。回拨时长: %dms", offset));
            }
        }

        if (lastTimestamp == timestamp) {
            // 同一毫秒内，序列号递增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 序列号用尽，等待下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个ID并以字符串形式返回。
     */
    public String nextIdString() {
        return String.valueOf(nextId());
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }
}
