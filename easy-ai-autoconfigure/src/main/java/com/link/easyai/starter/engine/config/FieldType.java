package com.link.easyai.starter.engine.config;

/**
 * Framework-level field types, decoupled from Java primitive types.
 * The engine uses these to uniformly handle serialization, parsing, and display.
 */
public enum FieldType {

    STRING,

    INTEGER,

    LONG,

    DECIMAL,

    BOOLEAN,

    STRING_LIST,

    INTEGER_LIST,

    OBJECT,

    OBJECT_LIST
}
