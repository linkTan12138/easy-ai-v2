package com.link.easyai.starter.engine.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 字段提取规则覆盖集 —— 数据库 {@code ai_task_config} 表 config_json 的新 schema。
 * <p>
 * 框架以注解（@AiTask / @AiTaskParam / @AiExtract）定义任务结构与默认提取规则，
 * 数据库配置仅用于<b>按字段补充/覆盖提取规则描述</b>（description / examples / rules 等提示词内容）。
 * <p>
 * config_json 结构示例：
 * <pre>
 * {
 *   "taskType": "CREATE_TICKET",
 *   "fields": {
 *     "phone":       { "description": "...", "examples": ["13800138000"], "rules": ["必须是11位数字"] },
 *     "description": { "rules": ["如包含投诉意图需提取投诉对象和事由"] }
 *   }
 * }
 * </pre>
 * 解析时校验 {@code taskType} 与表行一致；合并时按 {@code fieldCode} 精确匹配注解字段，
 * 引用不存在的字段会在发布/加载时报清晰错误，避免静默失效。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldExtractionOverrides {

    /** 冗余任务类型，用于与表行 {@code task_type} 一致性校验（表列为权威） */
    private String taskType;

    /** 冗余版本号（表行 version 为权威） */
    private Integer version;

    /** 字段提取覆盖：fieldCode → extraction 覆盖（非空字段才覆盖） */
    private Map<String, ExtractionConfig> fields;
}
