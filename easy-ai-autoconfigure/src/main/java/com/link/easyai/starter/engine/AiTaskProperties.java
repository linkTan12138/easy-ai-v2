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
    }

    @Data
    public static class Lifecycle {

        /** Maximum conversation turns per task. Exceeding marks the task FAILED. */
        private int maxTurns = 10;

        /** Task timeout in minutes. Expired tasks are marked EXPIRED. */
        private int timeoutMinutes = 30;

        /** Whether to enable recovery guidance when resuming an incomplete task. */
        private boolean recoveryGuidanceEnabled = true;
    }
}
