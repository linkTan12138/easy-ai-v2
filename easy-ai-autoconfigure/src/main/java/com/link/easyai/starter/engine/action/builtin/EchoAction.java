package com.link.easyai.starter.engine.action.builtin;

import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.action.AiAction;
import com.link.easyai.starter.engine.context.ActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Sample {@link com.link.easyai.starter.engine.action.ActionExecutor} that echoes
 * back the collected parameters as a success result.
 * <p>
 * This is a built-in demo action — business projects register their own
 * {@code @AiAction} beans (e.g. UPDATE_WAYBILL, CREATE_ORDER) and do not need
 * this one. It is useful for testing and for showing the wiring pattern.
 */
@AiAction(value = "ECHO",
        name = "回显演示",
        description = "内置演示动作，回显已收集的参数。仅用于测试和接线验证，不面向终端用户展示。",
        hidden = true)
public class EchoAction implements com.link.easyai.starter.engine.action.ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(EchoAction.class);

    @Override
    public String type() {
        return "ECHO";
    }

    @Override
    public ActionResult execute(ActionContext context) {
        Map<String, Object> params = context.getParameters();
        log.info("[EchoAction] executing with parameters: {}", params);

        String summary = "已收集参数: " + params;
        return ActionResult.success("演示动作执行成功！" + summary, params);
    }
}
