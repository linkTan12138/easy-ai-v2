package com.link.easyai.starter.engine.history;

import java.util.List;

/**
 * 对话历史管理器。
 * <p>
 * 维护每个会话的对话历史滑动窗口，用于：
 * <ul>
 *   <li>传递给 LLM 提升多轮上下文理解（如指代消解"刚才那个订单"）</li>
 *   <li>意图识别时提供上下文参考</li>
 *   <li>会话恢复时重建上下文</li>
 * </ul>
 * 滑动窗口策略：保留最近 N 轮对话（user+assistant 为一轮），
 * 超出窗口的旧消息自动丢弃，控制 token 用量。
 */
public interface ChatHistoryManager {

    /**
     * 加载会话的对话历史。
     *
     * @param sessionId 会话ID
     * @return 对话消息列表（按时间升序），无历史返回空列表
     */
    List<ChatMessage> loadHistory(String sessionId);

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
}
