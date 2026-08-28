package com.link.easyai.starter.engine.task;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行器注册表。
 * <p>
 * 启动时收集所有实现 {@link TaskExecutor} 和 {@link PostTaskExecutor} 的 Bean，
 * 按 type() 标识注册。
 */
@Component
public class TaskRegistry {

    private final Map<String, TaskExecutor> tasks = new ConcurrentHashMap<>();
    private final Map<String, PostTaskExecutor> postTasks = new ConcurrentHashMap<>();

    /**
     * 注册一个任务执行器。
     */
    public void register(TaskExecutor executor) {
        tasks.put(executor.type(), executor);
    }

    /**
     * 注册一个后置任务执行器。
     */
    public void register(PostTaskExecutor executor) {
        postTasks.put(executor.type(), executor);
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
