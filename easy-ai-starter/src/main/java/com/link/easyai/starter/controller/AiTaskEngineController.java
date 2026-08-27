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
