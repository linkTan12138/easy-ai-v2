package com.link.easyai.starter.controller;

import com.link.easyai.starter.domain.entity.AiChatMessage;
import com.link.easyai.starter.domain.vo.Response;
import com.link.easyai.starter.engine.history.ChatHistoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话历史查询 Controller。
 * <p>
 * 提供会话历史消息的查询、分页统计和清空接口。
 * 历史消息独立存储在 ai_chat_message 表中。
 * <p>
 * 多租户隔离：可通过 tenantId 参数指定租户（缺省为 "0"），
 * 同一 sessionId 在不同租户下查询到的是各自独立的历史。
 */
@RestController
@RequestMapping("/easyai/engine/history")
public class ChatHistoryController {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryController.class);

    @Autowired
    private ChatHistoryManager chatHistoryManager;

    /**
     * 查询会话的所有历史消息（按时间升序）。
     *
     * @param sessionId 会话ID
     * @param tenantId  租户ID（可选，缺省 "0"）
     * @return 消息列表
     */
    @GetMapping("/{sessionId}")
    public Response<List<AiChatMessage>> listMessages(@PathVariable String sessionId,
                                                      @RequestParam(required = false, defaultValue = "0") String tenantId) {
        log.debug("[ChatHistory] list all messages, session={}, tenant={}", sessionId, tenantId);
        List<AiChatMessage> messages = chatHistoryManager.listMessages(sessionId, tenantId);
        return Response.success(messages);
    }

    /**
     * 分页查询会话的历史消息。
     *
     * @param sessionId 会话ID
     * @param page      页码（从1开始），默认1
     * @param size      每页条数，默认20
     * @param tenantId  租户ID（可选，缺省 "0"）
     * @return 分页结果（含列表、总数、页码、每页条数）
     */
    @GetMapping("/{sessionId}/page")
    public Response<Map<String, Object>> pageMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, defaultValue = "0") String tenantId) {
        log.debug("[ChatHistory] page messages, session={}, page={}, size={}, tenant={}", sessionId, page, size, tenantId);

        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100; // 限制最大每页条数

        List<AiChatMessage> messages = chatHistoryManager.listMessages(sessionId, page, size, tenantId);
        long total = chatHistoryManager.countMessages(sessionId, tenantId);

        Map<String, Object> result = new HashMap<>();
        result.put("list", messages);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (total + size - 1) / size);

        return Response.success(result);
    }

    /**
     * 查询会话的消息总数。
     *
     * @param sessionId 会话ID
     * @param tenantId  租户ID（可选，缺省 "0"）
     * @return 消息总数
     */
    @GetMapping("/{sessionId}/count")
    public Response<Long> countMessages(@PathVariable String sessionId,
                                        @RequestParam(required = false, defaultValue = "0") String tenantId) {
        log.debug("[ChatHistory] count messages, session={}, tenant={}", sessionId, tenantId);
        long count = chatHistoryManager.countMessages(sessionId, tenantId);
        return Response.success(count);
    }

    /**
     * 清空会话的历史消息（逻辑删除）。
     *
     * @param sessionId 会话ID
     * @param tenantId  租户ID（可选，缺省 "0"）
     * @return 操作结果
     */
    @DeleteMapping("/{sessionId}")
    public Response<Void> clearMessages(@PathVariable String sessionId,
                                        @RequestParam(required = false, defaultValue = "0") String tenantId) {
        log.info("[ChatHistory] clear messages, session={}, tenant={}", sessionId, tenantId);
        chatHistoryManager.clearHistory(sessionId, tenantId);
        return Response.success(null);
    }
}
