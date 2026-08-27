package com.customer.llm;

import com.link.easyai.starter.llm.LLMConfig;
import com.link.easyai.starter.llm.LLMProvider;
import com.link.easyai.starter.llm.Message;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 客户自定义 LLM Provider 示例。
 * <p>
 * 接入步骤：
 * <ol>
 *   <li>依赖 easy-ai-starter（获取 LLMProvider / LLMConfig / Message 接口）</li>
 *   <li>实现 {@link LLMProvider} 接口</li>
 *   <li>必须提供构造函数 {@code public MyCustomLLMProvider(LLMConfig.ProviderConfig config)}</li>
 *   <li>编译为 jar 放入主应用 classpath</li>
 *   <li>配置 llm.provider 为完整类名，或通过 SPI 注册后填 getName() 返回值</li>
 * </ol>
 */
public class MyCustomLLMProvider implements LLMProvider {

    private final String apiKey;
    private final String endpoint;
    private final String model;

    /**
     * 约定：构造函数必须接收 {@link LLMConfig.ProviderConfig}。
     */
    public MyCustomLLMProvider(LLMConfig.ProviderConfig config) {
        this.apiKey = config.getApiKey();
        this.endpoint = config.getEndpoint();
        this.model = config.getModel();
    }

    @Override
    public String chat(List<Message> messages, LLMConfig config) {
        // 客户自己的 API 调用方式
        List<Map<String, String>> msgList = new ArrayList<>();
        for (Message msg : messages) {
            msgList.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", msgList);

        try (HttpResponse resp = HttpRequest.post(endpoint + "/v1/chat")
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)  // 客户自己的认证方式
                .body(JSONUtil.toJsonStr(body))
                .execute()) {

            if (!resp.isOk()) {
                throw new RuntimeException("自定义 LLM 接口异常，HTTP=" + resp.getStatus());
            }

            JSONObject json = JSONUtil.parseObj(resp.body());
            return json.getStr("result");
        }
    }

    @Override
    public Stream<String> streamChat(List<Message> messages, LLMConfig config) {
        throw new UnsupportedOperationException("该模型暂不支持流式输出");
    }

    @Override
    public String getName() {
        return "my_custom_llm";
    }
}
