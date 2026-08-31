package com.link.easyai.starter.engine.task;

import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行器注册表。
 * <p>
 * 启动时收集所有实现 {@link TaskExecutor} 和 {@link PostTaskExecutor} 的 Bean，
 * 以 {@link AiTask#value()} / {@link AiPostTask#value()} 为唯一标识注册。
 */
@Component
public class TaskRegistry {

    private final Map<String, TaskExecutor> tasks = new ConcurrentHashMap<>();
    private final Map<String, PostTaskExecutor> postTasks = new ConcurrentHashMap<>();

    /**
     * 注册一个任务执行器。
     * 标识取自 {@link AiTask#value()}；注解缺失或 value 为空时快速失败。
     */
    public void register(TaskExecutor executor) {
        AiTask annotation = resolveAnnotation(executor.getClass(), AiTask.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException(
                    "TaskExecutor 实现类缺少 @AiTask 注解或 value 为空: " + executor.getClass().getName());
        }
        tasks.put(annotation.value(), executor);
    }

    /**
     * 注册一个后置任务执行器。
     * 标识取自 {@link AiPostTask#value()}；注解缺失或 value 为空时快速失败。
     */
    public void register(PostTaskExecutor executor) {
        AiPostTask annotation = resolveAnnotation(executor.getClass(), AiPostTask.class);
        if (annotation == null || annotation.value() == null || annotation.value().isBlank()) {
            throw new IllegalStateException(
                    "PostTaskExecutor 实现类缺少 @AiPostTask 注解或 value 为空: " + executor.getClass().getName());
        }
        postTasks.put(annotation.value(), executor);
    }

    /**
     * 解析实例上的类型注解，兼容 CGLIB 代理子类（注解不继承，需回退查父类）。
     */
    private <A extends Annotation> A resolveAnnotation(Class<?> clazz, Class<A> annotationType) {
        A annotation = clazz.getAnnotation(annotationType);
        if (annotation == null && clazz.getSuperclass() != null) {
            annotation = clazz.getSuperclass().getAnnotation(annotationType);
        }
        return annotation;
    }

    /**
     * 按类型获取任务执行器。
     */
    public TaskExecutor getTask(String type) {
        return tasks.get(type);
    }

    /**
     * 按类型获取后置任务执行器。
     */
    public PostTaskExecutor getPostTask(String type) {
        return postTasks.get(type);
    }

    /**
     * 检查任务类型是否已注册。
     */
    public boolean containsTask(String type) {
        return tasks.containsKey(type);
    }

    /**
     * 检查后置任务类型是否已注册。
     */
    public boolean containsPostTask(String type) {
        return postTasks.containsKey(type);
    }

    /**
     * 返回所有已注册的主任务（不可修改）。
     * 用于功能介绍等场景动态列出所有能力。
     */
    public Collection<TaskExecutor> getAllTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    /**
     * 返回所有已注册的后置任务（不可修改）。
     */
    public Collection<PostTaskExecutor> getAllPostTasks() {
        return Collections.unmodifiableCollection(postTasks.values());
    }
}
