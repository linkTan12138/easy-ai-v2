package com.link.easyai.starter.service.impl.llm;

import cn.hutool.core.collection.CollectionUtil;
import com.link.easyai.starter.config.DoubaoConfig;
import com.link.easyai.starter.service.LargeLanguageModel;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 豆包大模型服务实现（旧版）。
 *
 * @deprecated 已迁移到插件化 Provider 体系，请使用
 * {@link com.link.easyai.starter.llm.LLMProviderFactory} 或
 * {@link com.link.easyai.starter.service.LargeLanguageModelFactory} 获取 Provider。
 * 内置实现见 {@link com.link.easyai.starter.llm.DoubaoProvider}。
 */
@Service
@Slf4j
@Deprecated
public class DoubaoService implements LargeLanguageModel {

    @Autowired
    private DoubaoConfig doubaoConfig;

    @Override
    public String chatCompletion(String msg) {
        ArkService arkService = ArkService.builder().apiKey(doubaoConfig.getKey()).baseUrl(doubaoConfig.getUrl()).build();

        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 创建用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                .content(msg) // 设置消息内容
                .build();

        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);

        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(doubaoConfig.getModel())// Replace with Model ID .
                .messages(chatMessages) // 设置消息列表
                .build();

        // 发送聊天完成请求并打印响应
        List<ChatCompletionChoice> choices = null;
        try {
            // 获取响应并打印每个选择的消息内容
            choices = arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
        if(CollectionUtil.isNotEmpty(choices)) return choices.get(0).getMessage().getContent().toString();
        return "";
    }

    @Override
    public String chatCompletion(String system, String msg) {
        ArkService arkService = ArkService.builder().apiKey(doubaoConfig.getKey()).baseUrl(doubaoConfig.getUrl()).build();

        // 初始化消息列表
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 创建用户消息
        ChatMessage userMessage = ChatMessage.builder()
                .role(ChatMessageRole.USER) // 设置消息角色为用户
                .content(msg) // 设置消息内容
                .build();

        // 将用户消息添加到消息列表
        chatMessages.add(userMessage);
        ChatMessage sysMessage = ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM) // 设置消息角色为用户
                .content(system) // 设置消息内容
                .build();
        chatMessages.add(sysMessage);

        // 创建聊天完成请求
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(doubaoConfig.getModel())// Replace with Model ID .
                .messages(chatMessages) // 设置消息列表
                .build();

        // 发送聊天完成请求并打印响应
        List<ChatCompletionChoice> choices = null;
        try {
            // 获取响应并打印每个选择的消息内容
            choices = arkService.createChatCompletion(chatCompletionRequest)
                    .getChoices();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // 关闭服务执行器
            arkService.shutdownExecutor();
        }
        if(CollectionUtil.isNotEmpty(choices)) return choices.get(0).getMessage().getContent().toString();
        return "";
    }

    @Override
    public <T> T chatCompletion(String system, String msg, Class<T> clazz) {
        return null;
    }
}
