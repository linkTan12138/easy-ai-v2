package com.link.easyai.starter.engine.action;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Marks a class as a pluggable post-action executor.
 * <p>
 * Example:
 * <pre>
 * @AiPostAction("WRITE_TRACK")
 * public class WriteTrackPostAction implements PostActionExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AiPostAction {

    /**
     * Post-action type identifier, e.g. "WRITE_TRACK", "SEND_MESSAGE".
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();
}
