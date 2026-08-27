package com.link.easyai.starter.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * Entity for the <code>ai_task_config</code> table: versioned AI task engine
 * configurations (DRAFT → PUBLISHED → DISABLED lifecycle).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_task_config")
public class AiTaskConfigRecord extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Task type identifier, e.g. "ORDER_UPDATE" */
    private String taskType;

    /** Config version number */
    private Integer version;

    /** Human-readable config name */
    private String name;

    /** Full AiTaskConfig serialized as JSON */
    private String configJson;

    /** Lifecycle status: DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** When this version was published (null while DRAFT) */
    private Date publishedTime;

    /** Config lifecycle statuses */
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";
}
