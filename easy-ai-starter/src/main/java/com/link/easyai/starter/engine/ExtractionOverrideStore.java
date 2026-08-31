package com.link.easyai.starter.engine;

import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;

import java.util.List;

/**
 * 字段提取规则覆盖的存储接口（数据库实现为 {@link DefaultAiTaskConfigService}）。
 * <p>
 * 数据库配置只负责<b>按字段补充/覆盖提取规则描述</b>（@AiExtract 的提示词内容），
 * 任务结构与执行逻辑一律来自注解。存储按租户两级作用域：
 * <ul>
 *   <li>{@code tenantId = null} → 全局默认模板（所有租户共享）</li>
 *   <li>{@code tenantId = 具体值} → 租户私有覆盖（优先级高于全局）</li>
 * </ul>
 * 读取时遵循「租户优先、全局兜底」：先查租户作用域，没有再查全局作用域。
 * 无任何覆盖时返回 {@code null}（表示使用注解默认值，不是错误）。
 */
public interface ExtractionOverrideStore {

    /**
     * 获取最新已发布的字段提取覆盖（租户优先、全局兜底）。
     *
     * @param taskType 任务类型
     * @param tenantId 租户 ID；null 或空白表示只看全局作用域
     * @return 覆盖集；无任何发布覆盖时返回 null
     */
    FieldExtractionOverrides getPublishedOverrides(String taskType, String tenantId);

    /**
     * 获取指定版本的字段提取覆盖（任意状态，供任务恢复绑定版本使用）。
     *
     * @param taskType 任务类型
     * @param version  配置版本号
     * @param tenantId 租户 ID；null 或空白表示只看全局作用域
     * @return 覆盖集；不存在时返回 null
     */
    FieldExtractionOverrides getOverrides(String taskType, Integer version, String tenantId);

    /**
     * 获取最新已发布覆盖的版本号。
     *
     * @return 版本号；无发布覆盖时返回 null
     */
    Integer getLatestVersion(String taskType, String tenantId);

    // ---- 生命周期管理（DRAFT → PUBLISHED → DISABLED）----

    /**
     * 保存覆盖草稿。若 (tenantId, taskType, version) 已存在 DRAFT 则更新，否则插入。
     *
     * @param taskType  任务类型
     * @param tenantId  租户 ID；null 表示全局模板
     * @param overrides 覆盖集（version 为空时自动分配下一个版本）
     * @return 保存后的记录
     */
    AiTaskConfigRecord saveDraft(String taskType, String tenantId, FieldExtractionOverrides overrides);

    /**
     * 发布草稿。同一 (tenantId, taskType) 下旧 PUBLISHED 版本自动 DISABLED。
     */
    AiTaskConfigRecord publish(String taskType, Integer version, String tenantId);

    /**
     * 禁用已发布版本。已绑定该版本的在途任务仍继续使用。
     */
    AiTaskConfigRecord disable(String taskType, Integer version, String tenantId);

    /**
     * 按任务类型（可选）和租户作用域列出覆盖记录，版本降序。
     */
    List<AiTaskConfigRecord> list(String taskType, String tenantId);
}
