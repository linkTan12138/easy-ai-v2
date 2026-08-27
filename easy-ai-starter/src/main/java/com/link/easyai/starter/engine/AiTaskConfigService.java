package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.AiTaskConfig;

import java.util.List;

/**
 * Loads and manages task configuration by task type and version.
 * <p>
 * Implementations may load from:
 * - Database (ai_task_config table, with version support)
 * - JSON files
 * - External config service
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
     * Get the latest published config for a task type.
     * Used when creating a new task.
     *
     * @param taskType the task type, e.g. "ORDER_UPDATE"
     * @return the latest published config
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if no published config exists
     */
    AiTaskConfig getLatestPublished(String taskType);

    /**
     * Get a specific config version.
     * Used when resuming an existing task (bound to a specific version).
     *
     * @param taskType  the task type
     * @param version   the config version
     * @return the config for the specified version
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if config not found
     */
    AiTaskConfig get(String taskType, Integer version);

    /**
     * Get the latest version number for a task type.
     *
     * @param taskType the task type
     * @return the latest version number, or null if none exists
     */
    Integer getLatestVersion(String taskType);

    // ---- Lifecycle management ----

    /**
     * Save a config as DRAFT. If a DRAFT with the same (taskType, version) already
     * exists, it is updated; otherwise a new record is inserted.
     *
     * @param config    the config object (taskType and version must be set)
     * @return the saved config record (with id)
     */
    AiTaskConfigRecord saveDraft(AiTaskConfig config);

    /**
     * Publish a DRAFT config, making it available to new tasks.
     * If another version of the same taskType is currently PUBLISHED, it is
     * automatically DISABLED first (only one PUBLISHED version per taskType).
     *
     * @param taskType the task type
     * @param version  the version to publish
     * @return the published config record
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if the draft does not exist
     */
    AiTaskConfigRecord publish(String taskType, Integer version);

    /**
     * Disable a PUBLISHED config. Existing tasks bound to this version continue
     * to use it, but new tasks will not see it.
     *
     * @param taskType the task type
     * @param version  the version to disable
     * @return the disabled config record
     * @throws com.link.easyai.starter.engine.exception.ConfigNotFoundException if the config does not exist
     */
    AiTaskConfigRecord disable(String taskType, Integer version);

    /**
     * List all config records for a task type (any status).
     *
     * @param taskType the task type, or null to list all
     * @return list of config records ordered by version descending
     */
    List<AiTaskConfigRecord> list(String taskType);
}
