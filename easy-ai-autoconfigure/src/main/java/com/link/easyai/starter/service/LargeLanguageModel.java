package com.link.easyai.starter.service;

public interface LargeLanguageModel {

    String chatCompletion(String msg);
    String chatCompletion(String system, String msg);
    <T> T chatCompletion(String system, String msg, Class<T> clazz);

}
