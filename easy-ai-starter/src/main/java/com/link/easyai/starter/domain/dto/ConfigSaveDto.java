package com.link.easyai.starter.domain.dto;

import com.link.easyai.starter.engine.config.AiTaskConfig;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Request DTO for saving a config draft.
 * <p>
 * The <code>config</code> field is a full {@link AiTaskConfig} object.
 * If <code>version</code> is null, the service auto-assigns the next version.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigSaveDto {

    /** Full AiTaskConfig object */
    private AiTaskConfig config;
}
