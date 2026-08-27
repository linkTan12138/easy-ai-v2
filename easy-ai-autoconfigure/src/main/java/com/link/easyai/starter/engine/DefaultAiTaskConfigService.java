package com.link.easyai.starter.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.AiTaskConfig;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.mapper.AiTaskConfigRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Database-backed implementation of {@link AiTaskConfigService}.
 * <p>
 * Configs are stored as JSON in the <code>ai_task_config</code> table with a
 * DRAFT → PUBLISHED → DISABLED lifecycle and a unique (task_type, version) key.
 * <ul>
 *   <li>New tasks resolve the latest <b>PUBLISHED</b> version.</li>
 *   <li>Existing tasks resume their bound version regardless of status —
 *       a config republish never changes an in-flight task.</li>
 * </ul>
 * Parsed configs are cached per (taskType, version); the cache holds immutable
 * published snapshots.
 */
@Component
public class DefaultAiTaskConfigService implements AiTaskConfigService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiTaskConfigService.class);

    private final AiTaskConfigRecordMapper configMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, AiTaskConfig> cache = new ConcurrentHashMap<>();

    @Autowired
    public DefaultAiTaskConfigService(AiTaskConfigRecordMapper configMapper,
                                      ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiTaskConfig getLatestPublished(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            throw new ConfigNotFoundException(taskType);
        }

        AiTaskConfigRecord record = configMapper.selectOne(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_PUBLISHED)
                        .orderByDesc(AiTaskConfigRecord::getVersion)
                        .last("LIMIT 1"));

        if (record == null) {
            throw new ConfigNotFoundException(taskType);
        }
        return parseOrCached(record);
    }

    @Override
    public AiTaskConfig get(String taskType, Integer version) {
        if (taskType == null || taskType.isBlank() || version == null) {
            throw new ConfigNotFoundException(taskType, version);
        }

        // Cache-first for immutable snapshots (only PUBLISHED/DISABLED are cached)
        AiTaskConfig cached = cache.get(taskType + "|" + version);
        if (cached != null) {
            return cached;
        }

        AiTaskConfigRecord record = configMapper.selectOne(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getVersion, version)
                        .last("LIMIT 1"));

        if (record == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        // Note: status is intentionally NOT filtered here — an existing task
        // keeps using its bound version even after the version is disabled.
        return parseOrCached(record);
    }

    @Override
    public Integer getLatestVersion(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return null;
        }

        List<AiTaskConfigRecord> records = configMapper.selectList(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_PUBLISHED)
                        .orderByDesc(AiTaskConfigRecord::getVersion)
                        .last("LIMIT 1"));

        return records.isEmpty() ? null : records.get(0).getVersion();
    }

    /**
     * Parse the config JSON (with a per-version cache). The record's
     * taskType/version win over whatever the JSON declares, so the DB row is
     * always the source of truth for identity.
     */
    private AiTaskConfig parseOrCached(AiTaskConfigRecord record) {
        String cacheKey = record.getTaskType() + "|" + record.getVersion();
        AiTaskConfig cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String json = record.getConfigJson();
        if (json == null || json.isBlank()) {
            log.error("[AiTaskConfigService] config JSON is empty: taskType={}, version={}",
                    record.getTaskType(), record.getVersion());
            throw new ConfigNotFoundException(record.getTaskType(), record.getVersion());
        }

        try {
            AiTaskConfig config = objectMapper.readValue(json, AiTaskConfig.class);
            // DB identity wins
            config.setTaskType(record.getTaskType());
            config.setVersion(record.getVersion());
            if (config.getName() == null) {
                config.setName(record.getName());
            }
            if (isImmutableStatus(record.getStatus())) {
                cache.put(cacheKey, config);
            }
            return config;
        } catch (ConfigNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiTaskConfigService] failed to parse config JSON: taskType={}, version={}: {}",
                    record.getTaskType(), record.getVersion(), e.getMessage(), e);
            throw new ConfigNotFoundException(record.getTaskType(), record.getVersion());
        }
    }

    private boolean isImmutableStatus(String status) {
        // Only cache configs that can no longer change
        return AiTaskConfigRecord.STATUS_PUBLISHED.equals(status)
                || AiTaskConfigRecord.STATUS_DISABLED.equals(status);
    }

    // ---- Lifecycle management ----

    @Override
    public AiTaskConfigRecord saveDraft(AiTaskConfig config) {
        if (config.getTaskType() == null || config.getTaskType().isBlank()) {
            throw new ConfigNotFoundException(config.getTaskType());
        }
        if (config.getVersion() == null || config.getVersion() <= 0) {
            // Auto-assign next version if not specified
            config.setVersion(nextVersion(config.getTaskType()));
        }

        String taskType = config.getTaskType();
        Integer version = config.getVersion();

        // Look for an existing DRAFT with the same (taskType, version)
        AiTaskConfigRecord existing = configMapper.selectOne(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getVersion, version)
                        .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_DRAFT)
                        .last("LIMIT 1"));

        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.error("[AiTaskConfigService] failed to serialize config: taskType={}, version={}", taskType, version, e);
            throw new ConfigNotFoundException(taskType, version);
        }

        if (existing != null) {
            existing.setName(config.getName());
            existing.setConfigJson(json);
            configMapper.updateById(existing);
            return existing;
        }

        AiTaskConfigRecord record = new AiTaskConfigRecord();
        record.setTaskType(taskType);
        record.setVersion(version);
        record.setName(config.getName());
        record.setConfigJson(json);
        record.setStatus(AiTaskConfigRecord.STATUS_DRAFT);
        configMapper.insert(record);
        return record;
    }

    @Override
    public AiTaskConfigRecord publish(String taskType, Integer version) {
        AiTaskConfigRecord record = findRecord(taskType, version);
        if (record == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        if (!AiTaskConfigRecord.STATUS_DRAFT.equals(record.getStatus())) {
            log.warn("[AiTaskConfigService] publish called on non-DRAFT config: taskType={}, version={}, status={}",
                    taskType, version, record.getStatus());
            throw new ConfigNotFoundException(taskType, version);
        }

        // Disable any currently PUBLISHED version of the same taskType
        List<AiTaskConfigRecord> published = configMapper.selectList(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_PUBLISHED));
        for (AiTaskConfigRecord p : published) {
            p.setStatus(AiTaskConfigRecord.STATUS_DISABLED);
            configMapper.updateById(p);
            // Evict from cache since it's no longer immutable-as-published
            cache.remove(taskType + "|" + p.getVersion());
        }

        record.setStatus(AiTaskConfigRecord.STATUS_PUBLISHED);
        record.setPublishedTime(new Date());
        configMapper.updateById(record);
        return record;
    }

    @Override
    public AiTaskConfigRecord disable(String taskType, Integer version) {
        AiTaskConfigRecord record = findRecord(taskType, version);
        if (record == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        record.setStatus(AiTaskConfigRecord.STATUS_DISABLED);
        configMapper.updateById(record);
        // Evict from cache
        cache.remove(taskType + "|" + version);
        return record;
    }

    @Override
    public List<AiTaskConfigRecord> list(String taskType) {
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(AiTaskConfigRecord::getTaskType, taskType);
        }
        wrapper.orderByDesc(AiTaskConfigRecord::getVersion);
        return configMapper.selectList(wrapper);
    }

    private AiTaskConfigRecord findRecord(String taskType, Integer version) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .eq(AiTaskConfigRecord::getVersion, version)
                        .last("LIMIT 1"));
    }

    private int nextVersion(String taskType) {
        List<AiTaskConfigRecord> all = configMapper.selectList(
                new LambdaQueryWrapper<AiTaskConfigRecord>()
                        .eq(AiTaskConfigRecord::getTaskType, taskType)
                        .orderByDesc(AiTaskConfigRecord::getVersion)
                        .last("LIMIT 1"));
        if (all.isEmpty()) {
            return 1;
        }
        return all.get(0).getVersion() + 1;
    }
}
