package com.link.easyai.starter.engine.builder.fixtures;

import com.link.easyai.starter.engine.action.ActionExecutor;
import com.link.easyai.starter.engine.action.ActionResult;
import com.link.easyai.starter.engine.context.ActionContext;

/**
 * Test-only action executor shared by all fixture tasks.
 */
public class FixtureAction implements ActionExecutor {

    public static final String TYPE = "FIXTURE_ACTION";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ActionResult execute(ActionContext context) {
        return ActionResult.success("fixture ok", null);
    }
}
