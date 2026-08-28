package com.link.easyai.starter.engine.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 应用启动时自动将所有 {@link TaskExecutor} 和 {@link PostTaskExecutor} Bean
 * 注册到 {@link TaskRegistry}。
 */
@Component
public class TaskRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final TaskRegistry taskRegistry;

    @Autowired
    public TaskRegistrar(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        Map<String, TaskExecutor> taskBeans = ctx.getBeansOfType(TaskExecutor.class);
        for (TaskExecutor executor : taskBeans.values()) {
            taskRegistry.register(executor);
        }

        Map<String, PostTaskExecutor> postTaskBeans = ctx.getBeansOfType(PostTaskExecutor.class);
        for (PostTaskExecutor executor : postTaskBeans.values()) {
            taskRegistry.register(executor);
        }
    }
}
