package com.link.easyai.starter.engine.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics for the AI Task Engine.
 * <p>
 * Exposes the following metrics:
 * <ul>
 *   <li>{@code easyai_chat_total} (Counter) — chat turns, tags: taskType, result</li>
 *   <li>{@code easyai_task_duration_seconds} (Timer) — total task duration from create to complete</li>
 *   <li>{@code easyai_stage_duration_seconds} (Timer) — per-stage duration, tag: stage</li>
 *   <li>{@code easyai_extraction_success_total} (Counter) — LLM extraction success/failure</li>
 *   <li>{@code easyai_validation_fail_total} (Counter) — validation failures, tags: fieldCode, validator</li>
 *   <li>{@code easyai_field_collect_turns} (DistributionSummary) — turns per field collection</li>
 *   <li>{@code easyai_llm_tokens_total} (Counter) — LLM token consumption, tag: model</li>
 *   <li>{@code easyai_intent_confidence} (DistributionSummary) — intent recognition confidence distribution</li>
 * </ul>
 * All metrics are no-ops if no MeterRegistry is available (e.g. without spring-boot-starter-actuator).
 */
@Component
public class EngineMetrics {

    private final MeterRegistry registry;

    @Autowired
    public EngineMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    // ---- Chat metrics ----

    public void recordChat(String taskType, String result) {
        if (registry == null) return;
        Counter.builder("easyai_chat_total")
                .description("Total chat turns")
                .tag("taskType", nullToUnknown(taskType))
                .tag("result", nullToUnknown(result))
                .register(registry)
                .increment();
    }

    // ---- Task duration ----

    public Timer.Sample startTaskTimer() {
        if (registry == null) return null;
        return Timer.start(registry);
    }

    public void stopTaskTimer(Timer.Sample sample, String taskType) {
        if (registry == null || sample == null) return;
        sample.stop(Timer.builder("easyai_task_duration_seconds")
                .description("Task duration from create to complete")
                .tag("taskType", nullToUnknown(taskType))
                .register(registry));
    }

    // ---- Stage duration ----

    public Timer.Sample startStageTimer() {
        if (registry == null) return null;
        return Timer.start(registry);
    }

    public void stopStageTimer(Timer.Sample sample, String stage) {
        if (registry == null || sample == null) return;
        sample.stop(Timer.builder("easyai_stage_duration_seconds")
                .description("Pipeline stage duration")
                .tag("stage", nullToUnknown(stage))
                .register(registry));
    }

    // ---- Extraction metrics ----

    public void recordExtraction(boolean success) {
        if (registry == null) return;
        Counter.builder("easyai_extraction_success_total")
                .description("LLM extraction success/failure count")
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    // ---- Validation metrics ----

    public void recordValidationFailure(String fieldCode, String validator) {
        if (registry == null) return;
        Counter.builder("easyai_validation_fail_total")
                .description("Validation failure count")
                .tag("fieldCode", nullToUnknown(fieldCode))
                .tag("validator", nullToUnknown(validator))
                .register(registry)
                .increment();
    }

    // ---- Field collect turns ----

    public void recordFieldCollectTurns(double turns) {
        if (registry == null) return;
        DistributionSummary.builder("easyai_field_collect_turns")
                .description("Average turns to collect a field")
                .register(registry)
                .record(turns);
    }

    // ---- LLM tokens ----

    public void recordLlmTokens(String model, long tokens) {
        if (registry == null) return;
        Counter.builder("easyai_llm_tokens_total")
                .description("LLM token consumption")
                .tag("model", nullToUnknown(model))
                .register(registry)
                .increment(tokens);
    }

    // ---- Intent confidence ----

    public void recordIntentConfidence(double confidence) {
        if (registry == null) return;
        DistributionSummary.builder("easyai_intent_confidence")
                .description("Intent recognition confidence distribution")
                .register(registry)
                .record(confidence);
    }

    private String nullToUnknown(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }
}
