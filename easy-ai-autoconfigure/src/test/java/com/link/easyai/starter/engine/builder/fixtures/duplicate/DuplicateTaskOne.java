package com.link.easyai.starter.engine.builder.fixtures.duplicate;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;

/**
 * First declaration of taskType "DUP_TASK" — valid on its own.
 */
@AiTask(type = "DUP_TASK", name = "重复任务一", action = FixtureAction.class)
public class DuplicateTaskOne {

    private String first;
}
