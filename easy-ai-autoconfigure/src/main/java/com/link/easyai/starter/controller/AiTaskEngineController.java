package com.link.easyai.starter.controller;

import com.link.easyai.starter.domain.dto.ConfigSaveDto;
import com.link.easyai.starter.domain.dto.EngineChatDto;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.domain.vo.EngineChatVo;
import com.link.easyai.starter.domain.vo.Response;
import com.link.easyai.starter.engine.AiTaskConfigService;
import com.link.easyai.starter.engine.AiTaskEngine;
import com.link.easyai.starter.engine.AiTaskResponse;
import com.link.easyai.starter.engine.context.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the AI Task Engine.
 * <p>
 * Exposes two groups of endpoints:
 * <ol>
 *   <li><b>Chat</b> — POST /easyai/engine/chat — process a user message through the engine</li>
 *   <li><b>Config management</b> — CRUD for task configs (DRAFT → PUBLISHED → DISABLED)</li>
 * </ol>
 */
@RestController
@RequestMapping("/easyai/engine")
public class AiTaskEngineController {

    private static final Logger log = LoggerFactory.getLogger(AiTaskEngineController.class);

    @Autowired
    private AiTaskEngine aiTaskEngine;

    @Autowired
    private AiTaskConfigService configService;

    // ---- Chat ----

    /**
     * Process a single conversation turn.
     *
     * @param dto chat request (taskType, taskId, message, tenantId)
     * @return engine response (need-more or task-complete)
     */
    @PostMapping("/chat")
    public Response<EngineChatVo> chat(@RequestBody EngineChatDto dto) {
        log.info("[EngineController] chat: taskType={}, taskId={}", dto.getTaskType(), dto.getTaskId());

        TaskContext taskContext = TaskContext.builder()
                .taskId(dto.getTaskId())
                .taskType(dto.getTaskType())
                .tenantId(dto.getTenantId())
                .data(new HashMap<>())
                .build();

        AiTaskResponse response = aiTaskEngine.execute(
                dto.getTaskType(), dto.getTaskId(), dto.getMessage(), taskContext);

        return Response.success(EngineChatVo.from(response));
    }

    // ---- Config management ----

    /**
     * Save a config as DRAFT. If version is null, auto-assigns the next version.
     *
     * @param dto config save request
     * @return saved config record
     */
    @PostMapping("/config/save")
    public Response<AiTaskConfigRecord> saveDraft(@RequestBody ConfigSaveDto dto) {
        log.info("[EngineController] saveDraft: taskType={}", dto.getConfig().getTaskType());
        AiTaskConfigRecord record = configService.saveDraft(dto.getConfig());
        return Response.success(record);
    }

    /**
     * Publish a DRAFT config, making it available to new tasks.
     *
     * @param body contains taskType and version
     * @return published config record
     */
    @PostMapping("/config/publish")
    public Response<AiTaskConfigRecord> publish(@RequestBody Map<String, Object> body) {
        String taskType = (String) body.get("taskType");
        Integer version = toInt(body.get("version"));
        log.info("[EngineController] publish: taskType={}, version={}", taskType, version);
        AiTaskConfigRecord record = configService.publish(taskType, version);
        return Response.success(record);
    }

    /**
     * Disable a PUBLISHED config. Existing tasks keep their bound version.
     *
     * @param body contains taskType and version
     * @return disabled config record
     */
    @PostMapping("/config/disable")
    public Response<AiTaskConfigRecord> disable(@RequestBody Map<String, Object> body) {
        String taskType = (String) body.get("taskType");
        Integer version = toInt(body.get("version"));
        log.info("[EngineController] disable: taskType={}, version={}", taskType, version);
        AiTaskConfigRecord record = configService.disable(taskType, version);
        return Response.success(record);
    }

    /**
     * List config records for a task type (or all if taskType is null).
     *
     * @param taskType optional task type filter
     * @return list of config records ordered by version descending
     */
    @GetMapping("/config/list")
    public Response<List<AiTaskConfigRecord>> list(@RequestParam(required = false) String taskType) {
        List<AiTaskConfigRecord> records = configService.list(taskType);
        return Response.success(records);
    }

    /**
     * Get the latest published config for a task type.
     *
     * @param taskType the task type
     * @return the latest published config
     */
    @GetMapping("/config/latest")
    public Response<Object> latest(@RequestParam String taskType) {
        Object config = configService.getLatestPublished(taskType);
        return Response.success(config);
    }

    @SuppressWarnings("unchecked")
    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }
}
