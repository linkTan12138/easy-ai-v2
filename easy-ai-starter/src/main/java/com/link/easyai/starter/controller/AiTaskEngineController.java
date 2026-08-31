package com.link.easyai.starter.controller;

import com.link.easyai.starter.domain.dto.AutoChatDto;
import com.link.easyai.starter.domain.dto.ConfigSaveDto;
import com.link.easyai.starter.domain.dto.EngineChatDto;
import com.link.easyai.starter.domain.entity.AiTaskConfigRecord;
import com.link.easyai.starter.domain.vo.AutoChatVo;
import com.link.easyai.starter.domain.vo.EngineChatVo;
import com.link.easyai.starter.domain.vo.Response;
import com.link.easyai.starter.engine.AiChatService;
import com.link.easyai.starter.engine.AiTaskConfigService;
import com.link.easyai.starter.engine.AiTaskEngine;
import com.link.easyai.starter.engine.AiTaskResponse;
import com.link.easyai.starter.engine.ChatResponse;
import com.link.easyai.starter.engine.config.FieldExtractionOverrides;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.engine.exception.ConfigNotFoundException;
import com.link.easyai.starter.engine.exception.ConfigValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for the AI Task Engine.
 * <p>
 * Exposes two groups of endpoints:
 * <ol>
 *   <li><b>Auto Chat</b> — POST /easyai/engine/chat/auto — 自动意图识别，无需指定 taskType</li>
 *   <li><b>Chat</b> — POST /easyai/engine/chat — 指定 taskType 直接处理（绕过意图识别）</li>
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

    @Autowired
    private AiChatService aiChatService;

    // ---- Chat ----

    /**
     * 自动意图识别聊天接口。
     * <p>
     * 调用方无需指定 taskType，框架会通过 IntentEngine 自动识别用户意图，
     * 并路由到对应任务执行。支持多轮对话、任务切换、任务取消和会话超时。
     * <p>
     * 完整流程：用户消息 → 意图识别(LLM优先+关键词降级) → 任务路由 →
     * 参数提取/校验/归一化 → 动作执行 → 返回结果。
     *
     * @param dto 自动聊天请求（sessionId, message, tenantId）
     * @return 自动聊天响应（含意图识别结果和任务执行状态）
     */
    @PostMapping("/chat/auto")
    public Response<AutoChatVo> autoChat(@RequestBody AutoChatDto dto) {
        log.info("[EngineController] autoChat: sessionId={}", dto.getSessionId());

        TaskContext taskContext = TaskContext.builder()
                .tenantId(dto.getTenantId())
                .data(new HashMap<>())
                .build();

        ChatResponse response = aiChatService.chat(
                dto.getMessage(), dto.getSessionId(), taskContext);

        return Response.success(AutoChatVo.from(response));
    }

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
     * Save a field-extraction override as DRAFT. If version is null, auto-assigns
     * the next version. The override applies to a tenant scope (null = global).
     *
     * @param dto config save request (taskType, tenantId, version, fields)
     * @return saved config record
     */
    @PostMapping("/config/save")
    public Response<AiTaskConfigRecord> saveDraft(@RequestBody ConfigSaveDto dto) {
        log.info("[EngineController] saveDraft: taskType={}, tenantId={}", dto.getTaskType(), dto.getTenantId());
        FieldExtractionOverrides overrides = FieldExtractionOverrides.builder()
                .taskType(dto.getTaskType())
                .version(dto.getVersion())
                .fields(dto.getFields())
                .build();
        AiTaskConfigRecord record = configService.saveDraft(dto.getTaskType(), dto.getTenantId(), overrides);
        return Response.success(record);
    }

    /**
     * Publish a DRAFT config, making it available to new tasks.
     *
     * @param body contains taskType, version and optional tenantId
     * @return published config record
     */
    @PostMapping("/config/publish")
    public Response<AiTaskConfigRecord> publish(@RequestBody Map<String, Object> body) {
        String taskType = (String) body.get("taskType");
        Integer version = toInt(body.get("version"));
        String tenantId = (String) body.get("tenantId");
        log.info("[EngineController] publish: taskType={}, version={}, tenantId={}", taskType, version, tenantId);
        AiTaskConfigRecord record = configService.publish(taskType, version, tenantId);
        return Response.success(record);
    }

    /**
     * Disable a PUBLISHED config. Existing tasks keep their bound version.
     *
     * @param body contains taskType, version and optional tenantId
     * @return disabled config record
     */
    @PostMapping("/config/disable")
    public Response<AiTaskConfigRecord> disable(@RequestBody Map<String, Object> body) {
        String taskType = (String) body.get("taskType");
        Integer version = toInt(body.get("version"));
        String tenantId = (String) body.get("tenantId");
        log.info("[EngineController] disable: taskType={}, version={}, tenantId={}", taskType, version, tenantId);
        AiTaskConfigRecord record = configService.disable(taskType, version, tenantId);
        return Response.success(record);
    }

    /**
     * List config records for a task type (or all if taskType is null),
     * optionally scoped by tenant.
     *
     * @param taskType optional task type filter
     * @param tenantId optional tenant scope (null/blank = global)
     * @return list of config records ordered by version descending
     */
    @GetMapping("/config/list")
    public Response<List<AiTaskConfigRecord>> list(@RequestParam(required = false) String taskType,
                                                   @RequestParam(required = false) String tenantId) {
        List<AiTaskConfigRecord> records = configService.list(taskType, tenantId);
        return Response.success(records);
    }

    /**
     * Get the latest published config for a task type and tenant.
     *
     * @param taskType the task type
     * @param tenantId optional tenant scope (null/blank = global)
     * @return the latest published config (annotation + DB overrides merged)
     */
    @GetMapping("/config/latest")
    public Response<Object> latest(@RequestParam String taskType,
                                   @RequestParam(required = false) String tenantId) {
        Object config = configService.getLatestPublished(taskType, tenantId);
        return Response.success(config);
    }

    @SuppressWarnings("unchecked")
    private Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    // ---- exception mapping for config management ----

    /** 配置校验失败（如覆盖引用了不存在的字段）→ 400 */
    @ExceptionHandler(ConfigValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Object> handleConfigValidation(ConfigValidationException e) {
        log.warn("[EngineController] config validation failed: {}", e.getMessage());
        return Response.fail(null, HttpStatus.BAD_REQUEST.value(), e.getMessage());
    }

    /** 配置不存在（如任务未注解声明 / 覆盖缺失）→ 404 */
    @ExceptionHandler(ConfigNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Response<Object> handleConfigNotFound(ConfigNotFoundException e) {
        log.warn("[EngineController] config not found: {}", e.getMessage());
        return Response.fail(null, HttpStatus.NOT_FOUND.value(), e.getMessage());
    }
}
