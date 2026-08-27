package com.link.easyai.starter.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for the AI Task Engine chat endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineChatDto {

    /** Task type, e.g. "ORDER_UPDATE" */
    private String taskType;

    /** Task ID (unique per conversation session) */
    private String taskId;

    /** User's latest message */
    private String message;

    /** Tenant ID (optional, from security context) */
    private Long tenantId;
}
