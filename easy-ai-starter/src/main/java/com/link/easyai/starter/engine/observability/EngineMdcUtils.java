package com.link.easyai.starter.engine.observability;

import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC (Mapped Diagnostic Context) utilities for structured logging.
 * <p>
 * Injects taskId, taskType, fieldCode, stage, tenantId into the SLF4J MDC
 * so every log line in the pipeline carries structured context. Use with
 * try-with-resources or explicit clear to avoid MDC leakage across threads.
 * <p>
 * Sensitive fields (sensitive=true in @AiField) are automatically masked
 * when logged via {@link #maskIfSensitive}.
 */
public final class EngineMdcUtils {

    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_TASK_TYPE = "taskType";
    public static final String KEY_FIELD_CODE = "fieldCode";
    public static final String KEY_STAGE = "stage";
    public static final String KEY_TENANT_ID = "tenantId";
    public static final String KEY_MODEL = "model";

    private EngineMdcUtils() {
    }

    /**
     * Put task context into MDC. Returns an AutoCloseable that clears the keys.
     */
    public static MdcScope withTaskContext(String taskId, String taskType, String tenantId) {
        MDC.put(KEY_TASK_ID, nullToEmpty(taskId));
        MDC.put(KEY_TASK_TYPE, nullToEmpty(taskType));
        if (tenantId != null) {
            MDC.put(KEY_TENANT_ID, tenantId);
        }
        return new MdcScope(KEY_TASK_ID, KEY_TASK_TYPE, KEY_TENANT_ID);
    }

    /**
     * Put the current pipeline stage into MDC.
     */
    public static MdcScope withStage(String stage) {
        MDC.put(KEY_STAGE, stage);
        return new MdcScope(KEY_STAGE);
    }

    /**
     * Put the current field code into MDC.
     */
    public static MdcScope withField(String fieldCode) {
        MDC.put(KEY_FIELD_CODE, nullToEmpty(fieldCode));
        return new MdcScope(KEY_FIELD_CODE);
    }

    /**
     * Put the model name into MDC.
     */
    public static MdcScope withModel(String model) {
        MDC.put(KEY_MODEL, nullToEmpty(model));
        return new MdcScope(KEY_MODEL);
    }

    /**
     * Mask a value if the field is marked sensitive.
     *
     * @param value     the value to mask
     * @param sensitive whether the field is sensitive
     * @return "***" if sensitive, otherwise the value as string
     */
    public static String maskIfSensitive(Object value, boolean sensitive) {
        if (!sensitive || value == null) {
            return value == null ? "null" : String.valueOf(value);
        }
        return "***";
    }

    /**
     * Clear all engine MDC keys.
     */
    public static void clearAll() {
        MDC.remove(KEY_TASK_ID);
        MDC.remove(KEY_TASK_TYPE);
        MDC.remove(KEY_FIELD_CODE);
        MDC.remove(KEY_STAGE);
        MDC.remove(KEY_TENANT_ID);
        MDC.remove(KEY_MODEL);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * AutoCloseable scope that removes specified MDC keys on close.
     * Use with try-with-resources.
     */
    public static class MdcScope implements AutoCloseable {
        private final String[] keys;

        public MdcScope(String... keys) {
            this.keys = keys;
        }

        @Override
        public void close() {
            for (String key : keys) {
                MDC.remove(key);
            }
        }
    }
}
