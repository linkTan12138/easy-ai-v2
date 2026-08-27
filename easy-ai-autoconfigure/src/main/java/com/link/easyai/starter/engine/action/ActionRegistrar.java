package com.link.easyai.starter.engine.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Auto-registers all {@link ActionExecutor} and {@link PostActionExecutor} beans
 * into {@link ActionRegistry} on application startup.
 */
@Component
public class ActionRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private final ActionRegistry actionRegistry;

    @Autowired
    public ActionRegistrar(ActionRegistry actionRegistry) {
        this.actionRegistry = actionRegistry;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();

        Map<String, ActionExecutor> actionBeans = ctx.getBeansOfType(ActionExecutor.class);
        for (ActionExecutor executor : actionBeans.values()) {
            actionRegistry.register(executor);
        }

        Map<String, PostActionExecutor> postActionBeans = ctx.getBeansOfType(PostActionExecutor.class);
        for (PostActionExecutor executor : postActionBeans.values()) {
            actionRegistry.register(executor);
        }
    }
}
