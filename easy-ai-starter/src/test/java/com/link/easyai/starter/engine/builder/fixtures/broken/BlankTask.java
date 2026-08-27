package com.link.easyai.starter.engine.builder.fixtures.broken;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;

/**
 * @AiTask with blank type and name — both must be reported.
 */
@AiTask(type = "", name = "", action = FixtureAction.class)
public class BlankTask {

    private String only;
}
