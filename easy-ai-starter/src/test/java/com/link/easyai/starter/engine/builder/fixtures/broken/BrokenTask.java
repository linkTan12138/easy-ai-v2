package com.link.easyai.starter.engine.builder.fixtures.broken;

import com.link.easyai.starter.engine.annotation.AiField;
import com.link.easyai.starter.engine.annotation.AiMapping;
import com.link.easyai.starter.engine.annotation.AiPremise;
import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.annotation.AiValid;
import com.link.easyai.starter.engine.annotation.Mapping;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;

/**
 * Accumulates four different declaration errors — the builder must report
 * ALL of them in a single ConfigValidationException (fail-fast, full picture).
 */
@AiTask(type = "BROKEN_TASK", name = "坏任务", action = FixtureAction.class)
public class BrokenTask {

    @AiField(name = "a", required = true)
    @AiValid(by = UnregisteredValidator.class)
    private String a;

    @AiPremise("noSuchField != null")
    private String b;

    @AiMapping(@Mapping(target = "c", source = "$wrong"))
    private String c;

    @AiPremise("d != null")
    private String d;
}
