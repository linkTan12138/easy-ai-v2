package com.link.easyai.starter.engine.llm;

import com.link.easyai.starter.engine.AiTaskProperties;
import com.link.easyai.starter.service.LargeLanguageModel;
import com.link.easyai.starter.service.LargeLanguageModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    public LlmClient(LargeLanguageModelFactory llmFactory, AiTaskProperties properties) {
        this.llmFactory = llmFactory;
        this.properties = properties;
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
                String response = model.chatCompletion(system, user);
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
