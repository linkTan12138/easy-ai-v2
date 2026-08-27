package com.link.easyai.starter.engine.action;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for {@link ActionExecutor} and {@link PostActionExecutor} beans.
 * <p>
 * On startup, all beans implementing these interfaces are collected and registered
 * by their type() identifier.
 */
@Component
public class ActionRegistry {

    private final Map<String, ActionExecutor> actions = new ConcurrentHashMap<>();
    private final Map<String, PostActionExecutor> postActions = new ConcurrentHashMap<>();

    /**
     * Register an action executor.
     */
    public void register(ActionExecutor executor) {
        actions.put(executor.type(), executor);
    }

    /**
     * Register a post-action executor.
     */
    public void register(PostActionExecutor executor) {
        postActions.put(executor.type(), executor);
    }

    /**
     * Get an action executor by type.
     */
    public ActionExecutor getAction(String type) {
        return actions.get(type);
    }

    /**
     * Get a post-action executor by type.
     */
    public PostActionExecutor getPostAction(String type) {
        return postActions.get(type);
    }

    /**
     * Check if an action type is registered.
     */
    public boolean containsAction(String type) {
        return actions.containsKey(type);
    }

    /**
     * Check if a post-action type is registered.
     */
    public boolean containsPostAction(String type) {
        return postActions.containsKey(type);
    }

    /**
     * Return all registered main actions (unmodifiable).
     * Used by feature-intro / help actions to dynamically list capabilities.
     */
    public Collection<ActionExecutor> getAllActions() {
        return Collections.unmodifiableCollection(actions.values());
    }

    /**
     * Return all registered post-actions (unmodifiable).
     */
    public Collection<PostActionExecutor> getAllPostActions() {
        return Collections.unmodifiableCollection(postActions.values());
    }
}
