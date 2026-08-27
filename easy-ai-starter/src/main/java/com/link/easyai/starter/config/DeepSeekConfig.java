package com.link.easyai.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Data
@ConfigurationProperties(prefix = "large-language-model.deepseek.api")
public class DeepSeekConfig {

    private String key;
    private String url;
    private String model;

    public HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + key);
        return headers;
    }
}
