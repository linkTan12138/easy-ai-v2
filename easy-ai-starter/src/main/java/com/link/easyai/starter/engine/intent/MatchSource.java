package com.link.easyai.starter.engine.intent;

/**
 * Source of an intent recognition match.
 */
public enum MatchSource {
    /** Matched by keyword (high confidence, fast path). */
    KEYWORD,
    /** Matched by LLM classification. */
    LLM,
    /** No match — fallback response. */
    FALLBACK,
    /** Continued an existing task (no recognition needed). */
    CONTINUE
}
