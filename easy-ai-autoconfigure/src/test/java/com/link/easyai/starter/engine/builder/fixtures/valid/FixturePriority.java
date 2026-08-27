package com.link.easyai.starter.engine.builder.fixtures.valid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Integer-valued enum → FieldType.INTEGER + options.
 */
@Getter
@AllArgsConstructor
public enum FixturePriority {

    LOW(1, "低"),
    HIGH(9, "高");

    private final Integer value;
    private final String label;
}
