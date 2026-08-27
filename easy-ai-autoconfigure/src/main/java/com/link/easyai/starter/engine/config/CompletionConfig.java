package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Completion configuration: defines when a task is considered "all fields collected"
 * and ready for action execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionConfig {

    /** Field codes that must be VALID for the task to be considered complete */
    private List<String> requiredFields;

    /** Field codes that are optional but if present must be VALID */
    private List<String> optionalFields;
}
