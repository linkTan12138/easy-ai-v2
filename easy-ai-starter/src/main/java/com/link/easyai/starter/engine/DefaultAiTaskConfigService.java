package com.link.easyai.starter.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;
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
 * 字段提取规则覆盖的数据库存储实现。
 * <p>
 * 数据库配置只存储<b>字段提取规则覆盖</b>（{@link FieldExtractionOverrides}，见
 * {@code ai_task_config.config_json}），不再存储完整任务结构——任务结构与执行逻辑
 * 一律来自 {@code @AiTask} / {@code @AiTaskParam} 注解。
 * <p>
 * 租户两级作用域：{@code tenant_id = NULL} 表示全局默认模板；非空表示租户私有覆盖。
 * 读取遵循「租户优先、全局兜底」。无任何覆盖时返回 {@code null}（使用注解默认值）。
 * <p>
 * 版本生命周期：DRAFT → PUBLISHED → DISABLED，(tenant_id, task_type, version) 唯一键。
 * 新任务解析最新 PUBLISHED 版本；在途任务按绑定版本恢复（即使该版本已 DISABLED）。
 */
@Component
public class DefaultAiTaskConfigService implements ExtractionOverrideStore {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiTaskConfigService.class);

    private final AiTaskConfigRecordMapper configMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, FieldExtractionOverrides> cache = new ConcurrentHashMap<>();

    @Autowired
    public DefaultAiTaskConfigService(AiTaskConfigRecordMapper configMapper,
                                      ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.objectMapper = objectMapper;
    }

    // ---- Read: tenant-first, global fallback ----

    @Override
    public FieldExtractionOverrides getPublishedOverrides(String taskType, String tenantId) {
        if (taskType == null || taskType.isBlank()) {
            throw new ConfigNotFoundException(taskType);
        }

        // 租户作用域优先
        AiTaskConfigRecord tenantRecord = findLatestPublished(taskType, tenantId);
        if (tenantRecord != null) {
            return parseOrCached(tenantRecord);
        }
        // 全局作用域兜底
        AiTaskConfigRecord globalRecord = findLatestPublished(taskType, null);
        return globalRecord != null ? parseOrCached(globalRecord) : null;
    }

    @Override
    public FieldExtractionOverrides getOverrides(String taskType, Integer version, String tenantId) {
        if (taskType == null || taskType.isBlank() || version == null) {
            throw new ConfigNotFoundException(taskType, version);
        }

        AiTaskConfigRecord tenantRecord = findRecord(taskType, version, tenantId);
        if (tenantRecord != null) {
            return parseOrCached(tenantRecord);
        }
        AiTaskConfigRecord globalRecord = findRecord(taskType, version, null);
        return globalRecord != null ? parseOrCached(globalRecord) : null;
    }

    @Override
    public Integer getLatestVersion(String taskType, String tenantId) {
        AiTaskConfigRecord record = findLatestPublished(taskType, tenantId);
        if (record != null) {
            return record.getVersion();
        }
        // 租户无覆盖时看全局
        record = findLatestPublished(taskType, null);
        return record == null ? null : record.getVersion();
    }

    /**
     * 查询指定作用域下最新已发布记录。
     */
    private AiTaskConfigRecord findLatestPublished(String taskType, String tenantId) {
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<AiTaskConfigRecord>()
                .eq(AiTaskConfigRecord::getTaskType, taskType)
                .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_PUBLISHED);
        applyTenant(wrapper, tenantId);
        wrapper.orderByDesc(AiTaskConfigRecord::getVersion).last("LIMIT 1");
        return configMapper.selectOne(wrapper);
    }

    /**
     * 查询指定作用域 + 指定版本记录（任意状态，供任务恢复绑定版本）。
     */
    private AiTaskConfigRecord findRecord(String taskType, Integer version, String tenantId) {
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<AiTaskConfigRecord>()
                .eq(AiTaskConfigRecord::getTaskType, taskType)
                .eq(AiTaskConfigRecord::getVersion, version);
        applyTenant(wrapper, tenantId);
        wrapper.last("LIMIT 1");
        return configMapper.selectOne(wrapper);
    }

    private void applyTenant(LambdaQueryWrapper<AiTaskConfigRecord> wrapper, String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(AiTaskConfigRecord::getTenantId, tenantId);
        } else {
            wrapper.isNull(AiTaskConfigRecord::getTenantId);
        }
    }

    /**
     * 解析覆盖集 JSON（带按作用域的版本缓存）。DB 行的 taskType/version 为权威；
     * 若 JSON 内 taskType 与表行不一致则视为脏数据报错，防止覆盖串到其他任务。
     */
    private FieldExtractionOverrides parseOrCached(AiTaskConfigRecord record) {
        String cacheKey = cacheKey(record.getTenantId(), record.getTaskType(), record.getVersion());
        FieldExtractionOverrides cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String json = record.getConfigJson();
        if (json == null || json.isBlank()) {
            log.error("[AiTaskConfig] 覆盖配置 JSON 为空: tenant={}, taskType={}, version={}",
                    record.getTenantId(), record.getTaskType(), record.getVersion());
            throw new ConfigNotFoundException(record.getTaskType(), record.getVersion());
        }

        try {
            FieldExtractionOverrides overrides = objectMapper.readValue(json, FieldExtractionOverrides.class);
            // DB 身份优先，JSON 中的 taskType/version 仅用于防呆校验
            if (overrides.getTaskType() != null && !overrides.getTaskType().isBlank()
                    && !overrides.getTaskType().equals(record.getTaskType())) {
                log.error("[AiTaskConfig] config_json.taskType '{}' 与表行 task_type '{}' 不一致: tenant={}, version={}",
                        overrides.getTaskType(), record.getTaskType(), record.getTenantId(), record.getVersion());
                throw new ConfigNotFoundException(record.getTaskType(), record.getVersion());
            }
            overrides.setTaskType(record.getTaskType());
            overrides.setVersion(record.getVersion());
            if (isImmutableStatus(record.getStatus())) {
                cache.put(cacheKey, overrides);
            }
            return overrides;
        } catch (ConfigNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiTaskConfig] 覆盖配置 JSON 解析失败: tenant={}, taskType={}, version={}: {}",
                    record.getTenantId(), record.getTaskType(), record.getVersion(), e.getMessage());
            throw new ConfigNotFoundException(record.getTaskType(), record.getVersion());
        }
    }

    private boolean isImmutableStatus(String status) {
        return AiTaskConfigRecord.STATUS_PUBLISHED.equals(status)
                || AiTaskConfigRecord.STATUS_DISABLED.equals(status);
    }

    private String cacheKey(String tenantId, String taskType, Integer version) {
        return (tenantId == null ? "NULL" : tenantId) + "|" + taskType + "|" + version;
    }

    // ---- Lifecycle management ----

    @Override
    public AiTaskConfigRecord saveDraft(String taskType, String tenantId, FieldExtractionOverrides overrides) {
        if (taskType == null || taskType.isBlank()) {
            throw new ConfigNotFoundException(taskType);
        }
        String tenant = normalizeTenant(tenantId);
        Integer version = overrides != null ? overrides.getVersion() : null;
        if (version == null || version <= 0) {
            version = nextVersion(taskType, tenant);
        }

        // 查找同作用域同版本的已有 DRAFT，更新而非插入
        AiTaskConfigRecord existing = findDraft(taskType, version, tenant);
        if (overrides == null) {
            overrides = new FieldExtractionOverrides();
        }
        overrides.setTaskType(taskType);
        overrides.setVersion(version);

        String json;
        try {
            json = objectMapper.writeValueAsString(overrides);
        } catch (Exception e) {
            log.error("[AiTaskConfig] 覆盖配置序列化失败: tenant={}, taskType={}, version={}", tenant, taskType, version, e);
            throw new ConfigNotFoundException(taskType, version);
        }

        if (existing != null) {
            existing.setConfigJson(json);
            configMapper.updateById(existing);
            return existing;
        }

        AiTaskConfigRecord record = new AiTaskConfigRecord();
        record.setTaskType(taskType);
        record.setTenantId(tenant);
        record.setVersion(version);
        record.setConfigJson(json);
        record.setStatus(AiTaskConfigRecord.STATUS_DRAFT);
        configMapper.insert(record);
        return record;
    }

    @Override
    public AiTaskConfigRecord publish(String taskType, Integer version, String tenantId) {
        String tenant = normalizeTenant(tenantId);
        AiTaskConfigRecord record = findRecord(taskType, version, tenant);
        if (record == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        if (!AiTaskConfigRecord.STATUS_DRAFT.equals(record.getStatus())) {
            log.warn("[AiTaskConfig] publish 调用在非 DRAFT 配置上: tenant={}, taskType={}, version={}, status={}",
                    tenant, taskType, version, record.getStatus());
            throw new ConfigNotFoundException(taskType, version);
        }

        // 禁用同一作用域下当前 PUBLISHED 版本
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<AiTaskConfigRecord>()
                .eq(AiTaskConfigRecord::getTaskType, taskType)
                .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_PUBLISHED);
        applyTenant(wrapper, tenant);
        List<AiTaskConfigRecord> published = configMapper.selectList(wrapper);
        for (AiTaskConfigRecord p : published) {
            p.setStatus(AiTaskConfigRecord.STATUS_DISABLED);
            configMapper.updateById(p);
            cache.remove(cacheKey(p.getTenantId(), p.getTaskType(), p.getVersion()));
        }

        record.setStatus(AiTaskConfigRecord.STATUS_PUBLISHED);
        record.setPublishedTime(new Date());
        configMapper.updateById(record);
        return record;
    }

    @Override
    public AiTaskConfigRecord disable(String taskType, Integer version, String tenantId) {
        String tenant = normalizeTenant(tenantId);
        AiTaskConfigRecord record = findRecord(taskType, version, tenant);
        if (record == null) {
            throw new ConfigNotFoundException(taskType, version);
        }
        record.setStatus(AiTaskConfigRecord.STATUS_DISABLED);
        configMapper.updateById(record);
        cache.remove(cacheKey(record.getTenantId(), record.getTaskType(), record.getVersion()));
        return record;
    }

    @Override
    public List<AiTaskConfigRecord> list(String taskType, String tenantId) {
        String tenant = normalizeTenant(tenantId);
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<>();
        if (taskType != null && !taskType.isBlank()) {
            wrapper.eq(AiTaskConfigRecord::getTaskType, taskType);
        }
        applyTenant(wrapper, tenant);
        wrapper.orderByDesc(AiTaskConfigRecord::getVersion);
        return configMapper.selectList(wrapper);
    }

    private AiTaskConfigRecord findDraft(String taskType, Integer version, String tenant) {
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<AiTaskConfigRecord>()
                .eq(AiTaskConfigRecord::getTaskType, taskType)
                .eq(AiTaskConfigRecord::getVersion, version)
                .eq(AiTaskConfigRecord::getStatus, AiTaskConfigRecord.STATUS_DRAFT);
        applyTenant(wrapper, tenant);
        wrapper.last("LIMIT 1");
        return configMapper.selectOne(wrapper);
    }

    private int nextVersion(String taskType, String tenant) {
        LambdaQueryWrapper<AiTaskConfigRecord> wrapper = new LambdaQueryWrapper<AiTaskConfigRecord>()
                .eq(AiTaskConfigRecord::getTaskType, taskType);
        applyTenant(wrapper, tenant);
        wrapper.orderByDesc(AiTaskConfigRecord::getVersion).last("LIMIT 1");
        List<AiTaskConfigRecord> all = configMapper.selectList(wrapper);
        if (all.isEmpty()) {
            return 1;
        }
        return all.get(0).getVersion() + 1;
    }

    /** 空白租户归一为 null（全局作用域）。 */
    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? null : tenantId.trim();
    }
}
