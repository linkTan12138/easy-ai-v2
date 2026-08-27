package com.link.easyai.starter.service.impl.llm;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.link.easyai.starter.config.DeepSeekConfig;
import com.link.easyai.starter.service.LargeLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DeepSeekService implements LargeLanguageModel {

    @Autowired
    private DeepSeekConfig deepSeekConfig;

    @Override
    public String chatCompletion(String system, String msg) {
        /* ---------- 1. 组装消息 ---------- */
        List<Map<String, String>> messages = new ArrayList<>(2);
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", msg));

        /* ---------- 2. 组装请求体 ---------- */
        Map<String, Object> body = new HashMap<>();
        body.put("model", deepSeekConfig.getModel());
        body.put("messages", messages);
        body.put("stream", false);          // 明确关闭流式

        /* ---------- 3. 发起 POST ---------- */
        HttpResponse resp = HttpRequest.post(deepSeekConfig.getUrl())
                .header("Content-Type", "application/json")
                .header(deepSeekConfig.buildHeaders())
                .body(JSONUtil.toJsonStr(body))
                .execute();

        if (!resp.isOk()) {
            throw new RuntimeException("Kimi 接口异常，HTTP=" + resp.getStatus()
                    + "，body=" + resp.body());
        }

        /* ---------- 4. 提取回答 ---------- */
        JSONObject json = JSONUtil.parseObj(resp.body());
        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getStr("content");
    }

    @Override
    public String chatCompletion(String userMessage) {
        return "";
    }

    @Override
    public <T> T chatCompletion(String system, String msg, Class<T> clazz) {
        return null;
    }
}