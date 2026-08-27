package com.link.easyai.starter.engine.builder.fixtures.duplicate;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;

/**
 * Second declaration of the same taskType "DUP_TASK" — startup must fail
 * with a duplicate-taskType error naming both classes.
 */
@AiTask(type = "DUP_TASK", name = "重复任务二", action = FixtureAction.class)
public class DuplicateTaskTwo {

    private String second;
}
