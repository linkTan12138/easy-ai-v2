package com.link.easyai.starter.engine;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AI Task Engine.
 * <p>
 * Example application.yml:
 * <pre>
 * easy-ai:
 *   task-engine:
 *     enabled: true
 *     config-source: database   # database | json
 *     json-config-path: classpath:ai-task-configs/
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "easy-ai.task-engine")
public class AiTaskProperties {

    /** Whether to enable the AI Task Engine */
    private boolean enabled = true;

    /** Config source: "database" or "json" */
    private String configSource = "database";

    /** Path for JSON config files (when configSource=json) */
    private String jsonConfigPath = "classpath:ai-task-configs/";

    /** Whether to enable trace logging */
    private boolean traceEnabled = true;

    /** Annotation-based config source (code-defined tasks via @AiTask DTOs) */
    private final Annotation annotation = new Annotation();

    /** LLM engineering: retry, fallback, timeout, etc. */
    private final Llm llm = new Llm();

    /** Conversation lifecycle: max turns, timeout, etc. */
    private final Lifecycle lifecycle = new Lifecycle();

    /** Snowflake ID generator configuration (workerId / datacenterId). */
    private final Snowflake snowflake = new Snowflake();

    /** LLM resilience configuration (rate limiting, circuit breaking). */
    private final Resilience resilience = new Resilience();

    @Data
    public static class Annotation {

        /**
         * Whether to enable the annotation config source. When enabled, a
         * taskType declared via @AiTask is always served from code (version 1)
         * and shadowed database configs are ignored with a warning.
         */
        private boolean enabled = true;

        /**
         * Base packages to scan for @AiTask classes. Defaults to the Spring
         * Boot application package (AutoConfigurationPackages) when empty.
         */
        private String[] basePackages = {};
    }

    @Data
    public static class Llm {

        /** Maximum retry attempts for transient failures (timeout, 5xx, 429). */
        private int maxRetries = 3;

        /** Initial backoff delay in milliseconds for exponential backoff. */
        private long initialBackoffMs = 1000;

        /** Backoff multiplier for exponential backoff. */
        private double backoffMultiplier = 2.0;

        /**
         * Fallback model names in priority order. When the primary model fails
         * after maxRetries, the client tries each fallback in order.
         * Example: ["doubao", "deepseek"]
         */
        private String[] fallbackModels = {};

        /** Maximum input length for user messages (characters). Longer inputs are truncated. */
        private int maxInputLength = 2000;

        /** Whether to enable prompt injection detection. */
        private boolean injectionDetectionEnabled = true;

        /**
         * 是否开启 LLM 调用详细日志。
         * 开启后每次 LLM 调用都会输出完整的请求消息（system + user）和响应内容，
         * 便于调试提示词和排查问题。生产环境建议关闭。
         */
        private boolean logEnabled = false;
    }

    @Data
    public static class Lifecycle {

        /** Maximum conversation turns per task. Exceeding marks the task FAILED. */
        private int maxTurns = 10;

        /** Task timeout in minutes. Expired tasks are marked EXPIRED. */
        private int timeoutMinutes = 30;

        /** Whether to enable recovery guidance when resuming an incomplete task. */
        private boolean recoveryGuidanceEnabled = true;

        /**
         * 是否启用后台定时任务，主动将超过 timeout-minutes 未更新的处理中任务标记为 EXPIRED。
         * 默认启用。禁用后任务过期只能依赖会话层面的懒清理（用户下次访问时触发）。
         */
        private boolean expireEnabled = true;

        /**
         * 定时过期检查的固定延迟（毫秒）。默认 600000（10 分钟）。
         */
        private long expireIntervalMs = 600000L;
    }

    @Data
    public static class Snowflake {

        /**
         * 工作机器ID (0-31)。多实例部署时每个实例应配置不同的 workerId，
         * 否则可能产生重复ID。默认0，单实例部署无需修改。
         */
        private long workerId = 0L;

        /**
         * 数据中心ID (0-31)。跨数据中心部署时每个数据中心配置不同的 datacenterId。
         * 默认0。
         */
        private long datacenterId = 0L;
    }

    @Data
    public static class Resilience {

        /** 是否启用限流熔断保护。默认启用。 */
        private boolean enabled = true;

        /**
         * 限流配置：每秒允许的最大请求数（QPS）。
         * 超过此限制的请求将快速失败，避免LLM服务被打垮。默认10。
         */
        private int rateLimitPerSecond = 10;

        /**
         * 限流时间窗口（秒）。默认1秒。
         */
        private int rateLimitWindowSeconds = 1;

        /**
         * 熔断配置：滑动窗口大小（请求数）。
         * 当最近N个请求的失败率超过阈值时，熔断器打开。默认20。
         */
        private int circuitBreakerSlidingWindowSize = 20;

        /**
         * 熔断失败率阈值（百分比，0-100）。默认50。
         */
        private float circuitBreakerFailureRateThreshold = 50.0f;

        /**
         * 熔断打开后等待多久进入半开状态（秒）。默认30。
         */
        private int circuitBreakerWaitDurationInOpenStateSeconds = 30;

        /**
         * 半开状态允许通过的请求数。默认5。
         */
        private int circuitBreakerPermittedNumberOfCallsInHalfOpenState = 5;

        /**
         * 最小调用数（熔断器开始计算失败率前的最小请求数）。默认10。
         */
        private int circuitBreakerMinimumNumberOfCalls = 10;
    }
}
