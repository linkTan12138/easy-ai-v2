package com.link.easyai.starter.engine.llm;

import com.link.easyai.starter.engine.AiTaskProperties;
import com.link.easyai.starter.llm.LLMConfig;
import com.link.easyai.starter.llm.LLMProvider;
import com.link.easyai.starter.llm.LLMProviderFactory;
import com.link.easyai.starter.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resilient LLM client with retry, exponential backoff, rate limiting, circuit breaking, and fallback models.
 * <p>
 * Wraps {@link com.link.easyai.starter.llm.LLMProvider} calls with:
 * <ul>
 *   <li><b>Rate Limiting:</b> 滑动时间窗口限流，超过 QPS 限制时快速失败</li>
 *   <li><b>Circuit Breaking:</b> 连续失败超过阈值时熔断，熔断期间快速失败，一段时间后自动恢复</li>
 *   <li><b>Retry:</b> transient failures (timeout, 5xx, 429) retried with exponential backoff</li>
 *   <li><b>Fallback:</b> after primary model exhausts retries, tries each configured fallback model</li>
 *   <li><b>Non-retryable:</b> 4xx (except 429) fails immediately</li>
 * </ul>
 * 限流熔断使用轻量级原子变量实现，不依赖外部库。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LLMProviderFactory llmFactory;
    private final LLMConfig llmConfig;
    private final AiTaskProperties properties;

    // ---- 限流：滑动时间窗口 ----
    private final AtomicLong windowStartTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger windowRequestCount = new AtomicInteger(0);

    // ---- 熔断：连续失败计数 + 熔断状态 ----
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    @Autowired
    public LlmClient(LLMProviderFactory llmFactory, LLMConfig llmConfig, AiTaskProperties properties) {
        this.llmFactory = llmFactory;
        this.llmConfig = llmConfig;
        this.properties = properties;
        log.info("[LlmClient] initialized with built-in rate limiting + circuit breaking");
    }

    /**
     * Call the LLM with retry + fallback.
     */
    public String chatCompletion(String primary, String system, String user) {
        AiTaskProperties.Llm config = properties.getLlm();
        List<String> models = buildModelChain(primary, config.getFallbackModels());

        Exception lastException = null;
        for (String model : models) {
            try {
                return callWithRetry(model, system, user, config);
            } catch (LlmCallException e) {
                lastException = e;
                if (e.isNonRetryable()) {
                    log.warn("[LlmClient] model '{}' non-retryable failure, skipping fallback: {}",
                            model, e.getMessage());
                    break;
                }
                log.warn("[LlmClient] model '{}' failed after retries: {}", model, e.getMessage());
            }
        }

        String msg = "All models failed. Last error: " +
                (lastException != null ? lastException.getMessage() : "unknown");
        throw new LlmCallException(msg, false);
    }

    private List<String> buildModelChain(String primary, String[] fallbacks) {
        List<String> chain = new ArrayList<>();
        chain.add(primary);
        if (fallbacks != null) {
            for (String fb : fallbacks) {
                if (fb != null && !fb.isBlank() && !fb.equals(primary)) {
                    chain.add(fb);
                }
            }
        }
        return chain;
    }

    private String callWithRetry(String modelName, String system, String user,
                                  AiTaskProperties.Llm config) {
        // 1. 熔断检查
        checkCircuitBreaker();

        // 2. 限流检查
        checkRateLimit();

        LLMProvider model = resolveModel(modelName);
        int maxRetries = config.getMaxRetries();
        long backoff = config.getInitialBackoffMs();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[LlmClient] calling model '{}' (attempt {}/{})", modelName, attempt, maxRetries);
                List<Message> messages = new ArrayList<>();
                if (system != null && !system.isBlank()) {
                    messages.add(Message.system(system));
                }
                messages.add(Message.user(user));
                String response = model.chat(messages, llmConfig);
                if (response == null || response.isBlank()) {
                    throw new LlmCallException("Model returned empty response", true);
                }
                // 成功：重置连续失败计数
                consecutiveFailures.set(0);
                return response;
            } catch (LlmCallException e) {
                recordFailure();
                if (e.isNonRetryable()) {
                    throw e;
                }
                if (attempt < maxRetries) {
                    log.warn("[LlmClient] model '{}' attempt {} failed, retrying in {}ms: {}",
                            modelName, attempt, backoff, e.getMessage());
                    sleep(backoff);
                    backoff = (long) (backoff * config.getBackoffMultiplier());
                } else {
                    log.error("[LlmClient] model '{}' failed after {} attempts: {}",
                            modelName, maxRetries, e.getMessage());
                    throw e;
                }
            } catch (Exception e) {
                recordFailure();
                LlmCallException classified = classifyException(e);
                if (classified.isNonRetryable()) {
                    throw classified;
                }
                if (attempt < maxRetries) {
                    log.warn("[LlmClient] model '{}' attempt {} failed ({}), retrying in {}ms",
                            modelName, attempt, classified.getMessage(), backoff);
                    sleep(backoff);
                    backoff = (long) (backoff * config.getBackoffMultiplier());
                } else {
                    log.error("[LlmClient] model '{}' failed after {} attempts: {}",
                            modelName, maxRetries, classified.getMessage());
                    throw classified;
                }
            }
        }
        throw new LlmCallException("Unexpected retry loop exit", false);
    }

    // ---- 限流：滑动时间窗口 ----

    private void checkRateLimit() {
        AiTaskProperties.Resilience cfg = properties.getResilience();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        int maxRequests = cfg.getRateLimitPerSecond();
        long windowMs = cfg.getRateLimitWindowSeconds() * 1000L;

        long now = System.currentTimeMillis();
        long windowStart = windowStartTime.get();

        // 窗口过期，重置
        if (now - windowStart > windowMs) {
            if (windowStartTime.compareAndSet(windowStart, now)) {
                windowRequestCount.set(0);
            }
        }

        int count = windowRequestCount.incrementAndGet();
        if (count > maxRequests) {
            windowRequestCount.decrementAndGet();
            log.warn("[LlmClient] rate limit exceeded: {}/{} requests in {}ms",
                    count, maxRequests, windowMs);
            throw new LlmCallException("请求过于频繁，已触发限流保护", false);
        }
    }

    // ---- 熔断：连续失败计数 ----

    private void checkCircuitBreaker() {
        AiTaskProperties.Resilience cfg = properties.getResilience();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        long openUntil = circuitOpenUntil.get();
        if (openUntil > System.currentTimeMillis()) {
            log.warn("[LlmClient] circuit breaker is OPEN, rejecting request fast");
            throw new LlmCallException("LLM服务暂不可用（熔断器已打开），请稍后重试", false);
        }
    }

    private void recordFailure() {
        AiTaskProperties.Resilience cfg = properties.getResilience();
        if (cfg == null || !cfg.isEnabled()) {
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        int threshold = cfg.getCircuitBreakerSlidingWindowSize();
        if (failures >= threshold) {
            long openDuration = cfg.getCircuitBreakerWaitDurationInOpenStateSeconds() * 1000L;
            circuitOpenUntil.set(System.currentTimeMillis() + openDuration);
            log.warn("[LlmClient] circuit breaker OPENED after {} consecutive failures, will recover in {}ms",
                    failures, openDuration);
        }
    }

    // ---- 辅助方法 ----

    private LLMProvider resolveModel(String modelName) {
        return llmFactory.create(modelName);
    }

    private LlmCallException classifyException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("404") || msg.contains("401") || msg.contains("403")
                || msg.contains("Invalid") || msg.contains("invalid")) {
            return new LlmCallException(msg, true);
        }
        return new LlmCallException(msg, false);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("Retry sleep interrupted", false);
        }
    }
}
