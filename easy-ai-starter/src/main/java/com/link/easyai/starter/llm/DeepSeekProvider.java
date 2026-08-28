package com.link.easyai.starter.llm;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * DeepSeek 内置 Provider。
 * 使用 OpenAI 兼容的 /chat/completions 接口。
 */
public class DeepSeekProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);

    private static final String DEFAULT_ENDPOINT = "https://api.deepseek.com";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final String apiKey;
    private final String endpoint;
    private final String model;

    public DeepSeekProvider(LLMConfig.ProviderConfig config) {
        this.apiKey = config.getApiKey();
        this.endpoint = config.getEndpoint() != null ? config.getEndpoint() : DEFAULT_ENDPOINT;
        this.model = config.getModel() != null ? config.getModel() : DEFAULT_MODEL;
    }

    @Override
    public String chat(List<Message> messages, LLMConfig config) {
        List<Map<String, String>> msgList = convertMessages(messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", msgList);
        body.put("stream", false);

        String url = buildChatUrl(endpoint);
        log.debug("[DeepSeekProvider] POST {} model={}", url, model);

        try (HttpResponse resp = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(JSONUtil.toJsonStr(body))
                .execute()) {

            if (!resp.isOk()) {
                throw new RuntimeException("DeepSeek 接口异常，HTTP=" + resp.getStatus() + "，body=" + resp.body());
            }

            JSONObject json = JSONUtil.parseObj(resp.body());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getStr("content");
        }
    }

    @Override
    public Stream<String> streamChat(List<Message> messages, LLMConfig config) {
        throw new UnsupportedOperationException("DeepSeek 流式输出待实现");
    }

    @Override
    public String getName() {
        return "deepseek";
    }

    /**
     * 构建 chat/completions URL。
     * 如果 endpoint 已经包含 /chat/completions，则直接使用；否则拼接。
     */
    private String buildChatUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return DEFAULT_ENDPOINT + "/chat/completions";
        }
        if (endpoint.toLowerCase().endsWith("/chat/completions")) {
            return endpoint;
        }
        return endpoint + "/chat/completions";
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        List<Map<String, String>> list = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            list.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        return list;
    }
}
