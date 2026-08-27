package com.link.easyai.starter.engine.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Action configuration for a task.
 * The "type" maps to a registered {@link com.link.easyai.starter.engine.action.ActionExecutor}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionConfig {

    /** Main action type identifier, e.g. "UPDATE_WAYBILL" */
    private String type;

    /** Post-actions to execute after the main action succeeds */
    private List<String> postActions;

    /** Extra parameters for the action */
    private Map<String, Object> params;
}
