package com.link.easyai.starter.engine.observability;

import java.util.regex.Pattern;

/**
 * Prompt injection detection utility.
 * <p>
 * Detects typical prompt injection patterns in user input:
 * <ul>
 *   <li>"Ignore previous instructions" / "忽略以上指令"</li>
 *   <li>"System:" / "You are now..." role impersonation</li>
 *   <li>"Ignore all previous" / "忘记之前的"</li>
 *   <li>Excessive repetition or very long inputs (handled by length limit separately)</li>
 * </ul>
 * When injection is detected, the caller should downgrade to parameter extraction only
 * and not execute any instruction-like content from the user message.
 */
public final class PromptInjectionDetector {

    private static final Pattern[] INJECTION_PATTERNS = {
            // English patterns
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)forget\\s+(everything|all|previous)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)^system\\s*:"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|above)"),
            Pattern.compile("(?i)new\\s+instructions?\\s*:"),
            // Chinese patterns
            Pattern.compile("忽略(以上|之前|前面|所有)(的)?(指令|提示|规则|内容)"),
            Pattern.compile("忘记(之前|以上|所有)(的)?(一切|内容|指令)"),
            Pattern.compile("你现在是"),
            Pattern.compile("^系统\\s*[:：]"),
            Pattern.compile("无视(以上|之前)(的)?(指令|规则)"),
            Pattern.compile("新的指令\\s*[:：]"),
            Pattern.compile("不要(遵守|遵循|管)(之前|以上)(的)?(规则|指令)")
    };

    private PromptInjectionDetector() {
    }

    /**
     * Check if the user message contains prompt injection patterns.
     *
     * @param userMessage the user's message
     * @return true if injection patterns are detected
     */
    public static boolean isInjectionDetected(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userMessage).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Truncate the user message to the maximum allowed length.
     *
     * @param userMessage  the user's message
     * @param maxLength    maximum allowed length in characters
     * @return the truncated message
     */
    public static String truncateIfNeeded(String userMessage, int maxLength) {
        if (userMessage == null || userMessage.length() <= maxLength) {
            return userMessage;
        }
        return userMessage.substring(0, maxLength);
    }

    /**
     * Check if the message exceeds the maximum allowed length.
     */
    public static boolean isTooLong(String userMessage, int maxLength) {
        return userMessage != null && userMessage.length() > maxLength;
    }
}
