package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;

import java.util.List;

/**
 * Loads and manages task configuration by task type, tenant and version.
 * <p>
 * 框架以注解（@AiTask）定义任务结构与默认配置；数据库只提供字段提取规则覆盖。
 * 主实现 {@link AnnotationAiTaskConfigService} 负责把两者合并：
 * 租户覆盖 &gt; 全局覆盖 &gt; 注解默认。因此本接口返回的是<b>合并后的完整配置</b>。
 * <p>
 * The config service is responsible for returning the correct version:
 * - When creating a new task: return the latest PUBLISHED version
 * - When resuming an existing task: return the bound config version
 * <p>
 * Config lifecycle: DRAFT → PUBLISHED → DISABLED.
 * Only PUBLISHED configs are visible to new tasks; existing tasks keep their
 * bound version even after it is DISABLED.
 */
public interface AiTaskConfigService {

    /**
     * Get the latest published (merged) config for a task type and tenant.
     * Used when creating a new task.
     *
     * @param taskType the task type, e.g. "ORDER_UPDATE"
     * @param tenantId the tenant ID; null/blank resolves only the global scope
     * @return the latest published config (annotation + DB extraction overrides merged)
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if no config exists
     */
    AiTaskConfig getLatestPublished(String taskType, String tenantId);

    /**
     * Get a specific config version (merged).
     * Used when resuming an existing task (bound to a specific version).
     *
     * @param taskType  the task type
     * @param version   the config version
     * @param tenantId  the tenant ID
     * @return the config for the specified version
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if config not found
     */
    AiTaskConfig get(String taskType, Integer version, String tenantId);

    /**
     * Get the latest version number for a task type and tenant.
     *
     * @return the latest version number, or null if none exists
     */
    Integer getLatestVersion(String taskType, String tenantId);

    // ---- Lifecycle management (delegated to the extraction override store) ----

    /**
     * Save an extraction-override config as DRAFT. If a DRAFT with the same
     * (tenantId, taskType, version) already exists, it is updated; otherwise a
     * new record is inserted.
     *
     * @param taskType   the task type
     * @param tenantId   the tenant ID; null means the global template scope
     * @param overrides  the field extraction overrides (version auto-assigned if null)
     * @return the saved config record (with id)
     */
    AiTaskConfigRecord saveDraft(String taskType, String tenantId, FieldExtractionOverrides overrides);

    /**
     * Publish a DRAFT config, making it available to new tasks.
     * If another version of the same (tenantId, taskType) is currently PUBLISHED,
     * it is automatically DISABLED first (only one PUBLISHED version per scope).
     */
    AiTaskConfigRecord publish(String taskType, Integer version, String tenantId);

    /**
     * Disable a PUBLISHED config. Existing tasks bound to this version continue
     * to use it, but new tasks will not see it.
     */
    AiTaskConfigRecord disable(String taskType, Integer version, String tenantId);

    /**
     * List all config records for a task type (any status), optionally scoped by tenant.
     */
    List<AiTaskConfigRecord> list(String taskType, String tenantId);
}
