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
     * @return 消息列表
     */
    @GetMapping("/{sessionId}")
    public Response<List<AiChatMessage>> listMessages(@PathVariable String sessionId) {
        log.debug("[ChatHistory] list all messages, session={}", sessionId);
        List<AiChatMessage> messages = chatHistoryManager.listMessages(sessionId);
        return Response.success(messages);
    }

    /**
     * 分页查询会话的历史消息。
     *
     * @param sessionId 会话ID
     * @param page      页码（从1开始），默认1
     * @param size      每页条数，默认20
     * @return 分页结果（含列表、总数、页码、每页条数）
     */
    @GetMapping("/{sessionId}/page")
    public Response<Map<String, Object>> pageMessages(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("[ChatHistory] page messages, session={}, page={}, size={}", sessionId, page, size);

        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100; // 限制最大每页条数

        List<AiChatMessage> messages = chatHistoryManager.listMessages(sessionId, page, size);
        long total = chatHistoryManager.countMessages(sessionId);

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
     * @return 消息总数
     */
    @GetMapping("/{sessionId}/count")
    public Response<Long> countMessages(@PathVariable String sessionId) {
        log.debug("[ChatHistory] count messages, session={}", sessionId);
        long count = chatHistoryManager.countMessages(sessionId);
        return Response.success(count);
    }

    /**
     * 清空会话的历史消息（逻辑删除）。
     *
     * @param sessionId 会话ID
     * @return 操作结果
     */
    @DeleteMapping("/{sessionId}")
    public Response<Void> clearMessages(@PathVariable String sessionId) {
        log.info("[ChatHistory] clear messages, session={}", sessionId);
        chatHistoryManager.clearHistory(sessionId);
        return Response.success(null);
    }
}
