package com.link.easyai.starter.engine.llm;

import com.link.easyai.starter.engine.AiTaskProperties;
import com.link.easyai.starter.service.LargeLanguageModel;
import com.link.easyai.starter.service.LargeLanguageModelFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Resilient LLM client with retry, exponential backoff, and fallback models.
 * <p>
 * Wraps {@link LargeLanguageModel} calls with:
 * <ul>
 *   <li><b>Retry:</b> transient failures (timeout, 5xx, 429) retried with
 *       exponential backoff (1s -> 2s -> 4s by default).</li>
 *   <li><b>Fallback:</b> after primary model exhausts retries, tries each
 *       configured fallback model in order.</li>
 *   <li><b>Non-retryable:</b> 4xx (except 429) fails immediately - no retry,
 *       no fallback (the request itself is bad).</li>
 * </ul>
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LargeLanguageModelFactory llmFactory;
    private final AiTaskProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final boolean resilienceEnabled;

    @Autowired
    public LlmClient(LargeLanguageModelFactory llmFactory,
                     AiTaskProperties properties,
                     ObjectProvider<CircuitBreaker> circuitBreakerProvider,
                     ObjectProvider<RateLimiter> rateLimiterProvider) {
        this.llmFactory = llmFactory;
        this.properties = properties;
        this.circuitBreaker = circuitBreakerProvider.getIfAvailable();
        this.rateLimiter = rateLimiterProvider.getIfAvailable();
        this.resilienceEnabled = this.circuitBreaker != null && this.rateLimiter != null
                && properties.getResilience() != null
                && properties.getResilience().isEnabled();
        if (resilienceEnabled) {
            log.info("[LlmClient] resilience enabled: rate limiting + circuit breaking active");
        } else {
            log.info("[LlmClient] resilience disabled (not configured or enabled=false)");
        }
    }

    /**
     * Call the LLM with retry + fallback.
     *
     * @param primary the primary model name (e.g. "kimi")
     * @param system  the system prompt
     * @param user    the user message
     * @return the LLM response text
     * @throws LlmCallException if all models fail
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
                    log.warn("[LlmClient] model '{}' returned non-retryable error, aborting: {}",
                            model, e.getMessage());
                    throw e;
                }
                log.warn("[LlmClient] model '{}' failed after {} retries, trying next fallback: {}",
                        model, config.getMaxRetries(), e.getMessage());
            }
        }

        throw new LlmCallException("All LLM models failed: " + models, lastException, false);
    }

    private List<String> buildModelChain(String primary, String[] fallbacks) {
        List<String> chain = new ArrayList<>();
        if (primary != null && !primary.isBlank()) {
            chain.add(primary);
        }
        if (fallbacks != null) {
            for (String fb : fallbacks) {
                if (fb != null && !fb.isBlank() && !chain.contains(fb)) {
                    chain.add(fb);
                }
            }
        }
        return chain;
    }

    private String callWithRetry(String modelName, String system, String user,
                                  AiTaskProperties.Llm config) {
        LargeLanguageModel model = resolveModel(modelName);
        int maxRetries = config.getMaxRetries();
        long backoff = config.getInitialBackoffMs();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[LlmClient] calling model '{}' (attempt {}/{})", modelName, attempt, maxRetries);
                String response = executeWithResilience(model, system, user);
                if (response == null || response.isBlank()) {
                    throw new LlmCallException("Model returned empty response", true);
                }
                return response;
            } catch (LlmCallException e) {
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

    /**
     * 应用限流熔断后执行实际的 LLM 调用。
     * <p>
     * 执行顺序：限流检查 → 熔断包装 → 实际调用。
     * 限流被拒或熔断打开时，抛出 LlmCallException 触发重试/降级逻辑。
     */
    private String executeWithResilience(LargeLanguageModel model, String system, String user) {
        if (!resilienceEnabled) {
            return model.chatCompletion(system, user);
        }

        Supplier<String> call = () -> model.chatCompletion(system, user);

        // 1. 应用熔断
        if (circuitBreaker != null) {
            call = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        }

        // 2. 应用限流
        if (rateLimiter != null) {
            call = RateLimiter.decorateSupplier(rateLimiter, call);
        }

        try {
            return call.get();
        } catch (RequestNotPermitted e) {
            // 限流被拒
            log.warn("[LlmClient] rate limit exceeded, request rejected");
            throw new LlmCallException("请求过于频繁，已触发限流保护", false);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            // 熔断打开
            log.warn("[LlmClient] circuit breaker is OPEN, request rejected fast");
            throw new LlmCallException("LLM服务暂不可用（熔断器已打开），请稍后重试", false);
        }
    }

    private LargeLanguageModel resolveModel(String modelName) {
        try {
            return llmFactory.getLargeLanguageModel(modelName);
        } catch (Exception e) {
            throw new LlmCallException("Unknown model: " + modelName, e, true);
        }
    }

    private LlmCallException classifyException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        boolean nonRetryable = msg.contains("400") || msg.contains("401")
                || msg.contains("403") || msg.contains("404")
                || msg.contains("invalid") || msg.contains("bad request");
        return new LlmCallException(e.getMessage(), e, nonRetryable);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new LlmCallException("Retry interrupted", ie, false);
        }
    }
}
