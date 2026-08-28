package com.link.easyai.starter.engine.llm;

import com.link.easyai.starter.engine.AiTaskProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LLM 调用限流熔断配置。
 * <p>
 * 基于 Resilience4j 实现：
 * <ul>
 *   <li><b>限流 (RateLimiter)</b>：限制每秒最大请求数，防止LLM服务被打垮</li>
 *   <li><b>熔断 (CircuitBreaker)</b>：当失败率超过阈值时自动熔断，快速失败，
 *       避免级联故障；一段时间后自动进入半开状态尝试恢复</li>
 * </ul>
 * 可通过 {@code easy-ai.task-engine.resilience.enabled=false} 关闭。
 */
@Configuration
@ConditionalOnProperty(prefix = "easy-ai.task-engine.resilience", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class LlmResilienceConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmResilienceConfig.class);

    public static final String LLM_CIRCUIT_BREAKER_NAME = "llmCall";
    public static final String LLM_RATE_LIMITER_NAME = "llmCall";

    private final AiTaskProperties properties;

    @Autowired
    public LlmResilienceConfig(AiTaskProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CircuitBreakerRegistry llmCircuitBreakerRegistry() {
        AiTaskProperties.Resilience cfg = properties.getResilience();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(cfg.getCircuitBreakerSlidingWindowSize())
                .failureRateThreshold(cfg.getCircuitBreakerFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(cfg.getCircuitBreakerWaitDurationInOpenStateSeconds()))
                .permittedNumberOfCallsInHalfOpenState(cfg.getCircuitBreakerPermittedNumberOfCallsInHalfOpenState())
                .minimumNumberOfCalls(cfg.getCircuitBreakerMinimumNumberOfCalls())
                // LLM调用的异常都算失败
                .recordExceptions(Throwable.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        log.info("[LlmResilience] CircuitBreaker initialized: slidingWindow={}, failureRate={}%, " +
                        "waitInOpen={}s, halfOpenCalls={}, minCalls={}",
                cfg.getCircuitBreakerSlidingWindowSize(),
                cfg.getCircuitBreakerFailureRateThreshold(),
                cfg.getCircuitBreakerWaitDurationInOpenStateSeconds(),
                cfg.getCircuitBreakerPermittedNumberOfCallsInHalfOpenState(),
                cfg.getCircuitBreakerMinimumNumberOfCalls());
        return registry;
    }

    @Bean
    public RateLimiterRegistry llmRateLimiterRegistry() {
        AiTaskProperties.Resilience cfg = properties.getResilience();

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(cfg.getRateLimitPerSecond())
                .limitRefreshPeriod(Duration.ofSeconds(cfg.getRateLimitWindowSeconds()))
                .timeoutDuration(Duration.ofMillis(100)) // 限流等待超时，快速失败
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        log.info("[LlmResilience] RateLimiter initialized: limit={}/{}s",
                cfg.getRateLimitPerSecond(), cfg.getRateLimitWindowSeconds());
        return registry;
    }

    @Bean
    public CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreaker cb = registry.circuitBreaker(LLM_CIRCUIT_BREAKER_NAME);
        // 监听熔断事件，便于排查
        cb.getEventPublisher()
                .onStateTransition(event -> log.warn("[LlmResilience] CircuitBreaker state changed: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onError(event -> log.debug("[LlmResilience] CircuitBreaker error: {}", event.getThrowable().getMessage()))
                .onSuccess(event -> log.debug("[LlmResilience] CircuitBreaker success"));
        return cb;
    }

    @Bean
    public RateLimiter llmRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter(LLM_RATE_LIMITER_NAME);
    }
}
