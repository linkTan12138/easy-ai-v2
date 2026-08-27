package com.link.easyai.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;


@ConfigurationProperties(prefix = "large-language-model.doubao.api")
@Data
public class DoubaoConfig {
    
    private String key;
    private String url;
    private String model;

    @Bean
    public HttpHeaders doubaoHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + key);
        return headers;
    }
}