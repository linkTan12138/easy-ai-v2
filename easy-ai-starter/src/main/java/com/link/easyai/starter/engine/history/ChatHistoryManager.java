package com.link.easyai.starter.engine.history;

import com.link.easyai.starter.domain.entity.AiChatMessage;

import java.util.List;

/**
 * 对话历史管理器。
 * <p>
 * 维护每个会话的对话历史，用于：
 * <ul>
 *   <li>传递给 LLM 提升多轮上下文理解（如指代消解"刚才那个订单"）</li>
 *   <li>意图识别时提供上下文参考</li>
 *   <li>会话恢复时重建上下文</li>
 *   <li>前端查询历史消息记录</li>
 * </ul>
 * 滑动窗口策略：保留最近 N 轮对话（user+assistant 为一轮），
 * 超出窗口的旧消息自动丢弃，控制 token 用量。
 */
public interface ChatHistoryManager {

    /**
     * 加载会话的对话历史（用于 LLM 上下文，按时间升序）。
     *
     * @param sessionId 会话ID
     * @return 对话消息列表（按时间升序），无历史返回空列表
     */
    List<ChatMessage> loadHistory(String sessionId);

    /**
     * 加载指定任务的对话历史（按时间升序）。
     * <p>
     * 用于当前任务的参数抽取，只返回该任务创建后的消息，
     * 避免上一个任务的历史污染当前任务的字段抽取。
     *
     * @param sessionId 会话ID
     * @param taskId    任务ID
     * @return 该任务的对话消息列表（按时间升序），无历史返回空列表
     */
    List<ChatMessage> loadHistoryByTask(String sessionId, String taskId);

    /**
     * 追加一条用户消息到历史。
     *
     * @param sessionId 会话ID
     * @param content   用户消息内容
     */
    void appendUserMessage(String sessionId, String content);

    /**
     * 追加一条AI回复到历史。
     *
     * @param sessionId 会话ID
     * @param content   AI回复内容
     */
    void appendAssistantMessage(String sessionId, String content);

    /**
     * 追加一条用户消息到历史（带任务关联信息）。
     *
     * @param sessionId 会话ID
     * @param content   用户消息内容
     * @param taskId    关联的任务ID（可选）
     * @param taskType  任务类型（可选）
     * @param tenantId  租户ID（可选）
     */
    void appendUserMessage(String sessionId, String content, String taskId, String taskType, Long tenantId);

    /**
     * 追加一条AI回复到历史（带任务关联信息）。
     *
     * @param sessionId 会话ID
     * @param content   AI回复内容
     * @param taskId    关联的任务ID（可选）
     * @param taskType  任务类型（可选）
     * @param tenantId  租户ID（可选）
     */
    void appendAssistantMessage(String sessionId, String content, String taskId, String taskType, Long tenantId);

    /**
     * 清空会话的对话历史（任务完成/取消/会话重置时调用）。
     *
     * @param sessionId 会话ID
     */
    void clearHistory(String sessionId);

    /**
     * 将对话历史格式化为 prompt 文本片段。
     * <p>
     * 格式示例：
     * <pre>
     * [用户]：我要修改订单
     * [AI]：好的，请提供订单号
     * [用户]：US123
     * </pre>
     *
     * @param history 对话消息列表
     * @return 格式化后的文本，无历史返回空字符串
     */
    String formatForPrompt(List<ChatMessage> history);

    /**
     * 查询会话的所有历史消息（按时间升序，用于前端展示）。
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<AiChatMessage> listMessages(String sessionId);

    /**
     * 分页查询会话的历史消息。
     *
     * @param sessionId 会话ID
     * @param page      页码（从1开始）
     * @param size      每页条数
     * @return 消息列表
     */
    List<AiChatMessage> listMessages(String sessionId, int page, int size);

    /**
     * 统计会话的消息总数。
     *
     * @param sessionId 会话ID
     * @return 消息总数
     */
    long countMessages(String sessionId);
}
