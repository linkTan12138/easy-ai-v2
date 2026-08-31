package com.link.easyai.starter.domain.dto;

import com.link.easyai.starter.engine.config.ExtractionConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for saving a field-extraction override config draft.
 * <p>
 * 数据库配置只负责<b>字段提取规则覆盖</b>（description / examples / rules 等提示词内容），
 * 任务结构与执行逻辑一律来自注解。
 * <p>
 * 示例：
 * <pre>
 * {
 *   "taskType": "CREATE_TICKET",
 *   "tenantId": null,          // null 或省略 = 全局模板；非空 = 租户私有覆盖
 *   "version": null,           // 为空时服务端自动分配下一版本
 *   "fields": {
 *     "phone":       { "description": "...", "examples": ["13800138000"], "rules": ["必须是11位数字"] },
 *     "description": { "rules": ["如包含投诉意图需提取投诉对象和事由"] }
 *   }
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSaveDto {

    /** Task type，必须由 @AiTask 注解声明 */
    private String taskType;

    /** 租户 ID；null 或空白表示全局默认模板 */
    private String tenantId;

    /** 配置版本号；为空时自动分配下一版本 */
    private Integer version;

    /** 字段提取覆盖：fieldCode → extraction 覆盖（非空字段才覆盖） */
    private Map<String, ExtractionConfig> fields;
}
