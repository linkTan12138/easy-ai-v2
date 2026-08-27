package com.link.easyai.starter.engine.validation.builtin;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Shared helpers for built-in validators: value coercion and list handling.
 * <p>
 * LLM extraction values may arrive as a single value or a list (STRING_LIST fields).
 * Validators that support both use these helpers to iterate uniformly.
 */
public final class ValueUtils {

    private ValueUtils() {
    }

    /**
     * Treat a value as a list of elements: lists are returned as-is,
     * single values are wrapped in a singleton list.
     */
    public static List<Object> elements(Object value) {
        if (value instanceof Collection<?> c) {
            return List.copyOf(c);
        }
        return Collections.singletonList(value);
    }

    /**
     * Check whether the value is a list/collection.
     */
    public static boolean isList(Object value) {
        return value instanceof Collection;
    }

    /**
     * Null/blank/empty check.
     */
    public static boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    /**
     * String representation for matching (trimmed).
     */
    public static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}
