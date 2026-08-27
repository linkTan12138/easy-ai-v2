package com.link.easyai.starter.engine.builder.fixtures.broken;

import com.link.easyai.starter.engine.annotation.AiTask;
import com.link.easyai.starter.engine.builder.fixtures.FixtureAction;
import com.link.easyai.starter.engine.builder.fixtures.valid.FixturePriority;

import java.util.List;
import java.util.Map;

/**
 * Every field uses an unsupported Java type — each must produce a clear
 * "type not supported" error at build time (no silent fallback).
 */
@AiTask(type = "UNSUPPORTED_TYPE_TASK", name = "类型不支持", action = FixtureAction.class)
@SuppressWarnings({"rawtypes", "unused"})
public class UnsupportedTypeTask {

    private List rawList;

    private List<FixturePriority> enumList;

    private String[] names;

    private Map<String, String> attributes;
}
