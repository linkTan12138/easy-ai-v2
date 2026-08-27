package com.link.easyai.starter.engine.builder.fixtures.valid;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;

/**
 * Minimal fixture — everything derived by convention.
 */
@AiTask(type = "FIXTURE_TASK_B", name = "任务乙", action = FixtureAction.class)
public class FixtureTaskB {

    private String only;
}
