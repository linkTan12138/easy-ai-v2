package com.link.easyai.starter.engine.builder.fixtures.valid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * String-valued enum → FieldType.STRING + options.
 * The second constant has no getLabel() value difference — both provide labels.
 */
@Getter
@AllArgsConstructor
public enum FixtureStatus {

    ACTIVE("A", "激活"),
    INACTIVE("I", "停用");

    private final String value;
    private final String label;
}
