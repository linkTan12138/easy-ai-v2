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
 * 通用 OpenAI 兼容接口 Provider。
 * <p>
 * 只要客户的模型提供 OpenAI 兼容的 /chat/completions 端点，
 * 无需写代码，配置 endpoint + apiKey + model 即可使用。
 * 覆盖绝大多数自研 / 开源模型部署场景（Ollama、vLLM、LM Studio、OneAPI 等）。
 */
public class OpenAICompatibleProvider implements LLMProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAICompatibleProvider.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String name;

    public OpenAICompatibleProvider(LLMConfig.ProviderConfig config) {
        this.apiKey = config.getApiKey();
        this.baseUrl = config.getEndpoint();
        this.model = config.getModel();
        // 允许客户通过 extra.name 指定 provider 名称，默认 "openai_compatible"
        this.name = config.getExtra() != null && config.getExtra().get("name") != null
                ? config.getExtra().get("name").toString()
                : "openai_compatible";
    }

    @Override
    public String chat(List<Message> messages, LLMConfig config) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("openai_compatible 需要配置 endpoint");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("openai_compatible 需要配置 apiKey");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("openai_compatible 需要配置 model");
        }

        List<Map<String, String>> msgList = convertMessages(messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", msgList);
        body.put("stream", false);

        String url = baseUrl + "/chat/completions";
        log.debug("[OpenAICompatibleProvider] POST {} model={}", url, model);

        try (HttpResponse resp = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .body(JSONUtil.toJsonStr(body))
                .execute()) {

            if (!resp.isOk()) {
                throw new RuntimeException("OpenAI 兼容接口异常，HTTP=" + resp.getStatus() + "，body=" + resp.body());
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
        throw new UnsupportedOperationException("openai_compatible 流式输出待实现");
    }

    @Override
    public String getName() {
        return name;
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        List<Map<String, String>> list = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            list.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }
        return list;
    }
}
