package com.link.easyai.starter.controller;

import com.link.easyai.starter.domain.dto.AutoChatDto;
import com.link.easyai.starter.engine.AiChatService;
import com.link.easyai.starter.engine.ChatResponse;
import com.link.easyai.starter.engine.context.TaskContext;
import com.link.easyai.starter.domain.vo.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 流式输出控制器。
 * <p>
 * 提供 Server-Sent Events 接口，实现对话的流式输出体验。
 * <p>
 * 由于底层 LLM 调用目前为同步阻塞模式，本控制器采用"分阶段事件 + 按字符推送"的方式
 * 模拟流式输出，提升用户体验：
 * <ol>
 *   <li><b>start</b> 事件：对话开始</li>
 *   <li><b>thinking</b> 事件：正在处理（意图识别/参数提取中）</li>
 *   <li><b>token</b> 事件：内容片段（按字符推送最终结果）</li>
 *   <li><b>complete</b> 事件：完成，携带完整响应对象</li>
 *   <li><b>error</b> 事件：处理异常</li>
 * </ol>
 * 未来接入支持流式的 LLM API 后，可替换为真正的 token 级流式输出。
 */
@RestController
@RequestMapping("/easyai/engine")
public class SseChatController {

    private static final Logger log = LoggerFactory.getLogger(SseChatController.class);

    /** 按字符推送时的间隔（毫秒），模拟打字机效果 */
    private static final long TOKEN_INTERVAL_MS = 15L;

    /** SSE 连接超时时间（毫秒），默认5分钟 */
    private static final long SSE_TIMEOUT_MS = 300000L;

    private final AiChatService aiChatService;

    /** 异步执行线程池，避免阻塞 Tomcat 请求线程 */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "easyai-sse-worker");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public SseChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * SSE 流式聊天接口（自动意图识别）。
     * <p>
     * 与 {@code POST /easyai/engine/chat/auto} 功能相同，但通过 SSE 流式返回。
     *
     * @param dto 聊天请求（sessionId, message, tenantId）
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AutoChatDto dto) {
        log.info("[SseChatController] stream chat: sessionId={}", dto.getSessionId());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        executor.execute(() -> {
            try {
                // 1. 发送 start 事件
                sendEvent(emitter, "start", "{\"status\":\"started\"}");

                // 2. 发送 thinking 事件
                sendEvent(emitter, "thinking", "{\"status\":\"processing\"}");

                // 3. 执行实际的聊天逻辑（同步阻塞）
                TaskContext taskContext = TaskContext.builder()
                        .tenantId(dto.getTenantId())
                        .data(new HashMap<>())
                        .build();

                ChatResponse response = aiChatService.chat(dto.getMessage(), dto.getSessionId(), taskContext);

                // 4. 按字符推送最终结果（模拟流式）
                String message = response.getMessage() != null ? response.getMessage() : "";
                StringBuilder buffer = new StringBuilder();
                for (int i = 0; i < message.length(); i++) {
                    buffer.append(message.charAt(i));
                    // 每3个字符推送一次，减少事件数量
                    if (i % 3 == 2 || i == message.length() - 1) {
                        sendEvent(emitter, "token",
                                "{\"content\":\"" + escapeJson(buffer.toString()) + "\"}");
                        buffer.setLength(0);
                        Thread.sleep(TOKEN_INTERVAL_MS);
                    }
                }

                // 5. 发送 complete 事件，携带完整响应
                String completeJson = buildCompleteJson(response);
                sendEvent(emitter, "complete", completeJson);

                emitter.complete();
                log.info("[SseChatController] stream completed: sessionId={}", dto.getSessionId());

            } catch (Exception e) {
                log.error("[SseChatController] stream error: sessionId={}", dto.getSessionId(), e);
                try {
                    sendEvent(emitter, "error",
                            "{\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                } catch (IOException ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, String data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildCompleteJson(ChatResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"taskId\":\"").append(escapeJson(response.getTaskId())).append("\",");
        sb.append("\"message\":\"").append(escapeJson(response.getMessage())).append("\",");
        sb.append("\"completed\":").append(response.isCompleted()).append(",");
        sb.append("\"needMore\":").append(response.isNeedMore()).append(",");
        sb.append("\"clarification\":").append(response.isClarification()).append(",");
        sb.append("\"taskType\":\"").append(escapeJson(response.getTaskType())).append("\"");
        sb.append("}");
        return sb.toString();
    }
}
