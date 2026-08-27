package com.link.easyai.starter.config;

import com.link.easyai.starter.domain.annotation.AiScene;
import com.link.easyai.starter.service.AiSceneProcessor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AiSceneHolder implements ApplicationContextAware {

    private static final Map<Integer, AiSceneProcessor> SCENE_MAP = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        // 1. 拿到所有被 @AiScene 标识的 Bean
        Map<String, Object> beans = ctx.getBeansWithAnnotation(AiScene.class);

        // 2. 以注解的 value() 作为 key，Bean 作为 value
        beans.values().forEach(bean -> {
            AiScene anno = bean.getClass().getAnnotation(AiScene.class);
            int key = anno.value();
            // 如果有重复 key，后面覆盖前面；也可以抛异常，按业务要求调整
            SCENE_MAP.put(key, (AiSceneProcessor) bean);
        });
    }

    public static AiSceneProcessor getSceneBySceneCode(Integer sceneCode) {
        return SCENE_MAP.get(sceneCode);
    }
}
