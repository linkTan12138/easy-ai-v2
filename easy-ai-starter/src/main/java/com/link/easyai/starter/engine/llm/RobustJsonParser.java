package com.link.easyai.starter.engine.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust JSON parser for LLM responses.
 * <p>
 * LLMs often return JSON with:
 * <ul>
 *   <li>Markdown code fences ({@code ```json ... ```})</li>
 *   <li>Surrounding prose before/after the JSON</li>
 *   <li>Trailing commas</li>
 *   <li>Single quotes instead of double quotes</li>
 *   <li>Comments ({@code // ...} or {@code /* ... *\/})</li>
 * </ul>
 * This utility cleans the response before parsing, and falls back to
 * regex-based extraction if standard parsing fails.
 */
public class RobustJsonParser {

    private static final Logger log = LoggerFactory.getLogger(RobustJsonParser.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RobustJsonParser() {
    }

    /**
     * Parse a potentially messy LLM response into a JsonNode.
     *
     * @param raw the raw LLM response
     * @return the parsed JsonNode
     * @throws IllegalArgumentException if no valid JSON can be extracted
     */
    public static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Empty response");
        }

        // Step 1: Strip markdown code fences
        String cleaned = stripCodeFences(raw);

        // Step 2: Extract JSON object/array from surrounding prose
        cleaned = extractJson(cleaned);

        // Step 3: Try direct parse
        try {
            return MAPPER.readTree(cleaned);
        } catch (Exception e) {
            log.debug("[RobustJsonParser] direct parse failed, attempting repair: {}", e.getMessage());
        }

        // Step 4: Repair common JSON issues and retry
        String repaired = repairJson(cleaned);
        try {
            return MAPPER.readTree(repaired);
        } catch (Exception e) {
            log.warn("[RobustJsonParser] parse failed after repair: {}", e.getMessage());
            throw new IllegalArgumentException("Failed to parse JSON from LLM response", e);
        }
    }

    /**
     * Strip markdown code fences (```json ... ``` or ``` ... ```).
     */
    static String stripCodeFences(String raw) {
        String s = raw.trim();
        // Remove opening fence with optional language tag
        s = s.replaceAll("^```[a-zA-Z]*\\s*", "");
        // Remove closing fence
        s = s.replaceAll("```\\s*$", "");
        return s.trim();
    }

    /**
     * Extract the outermost JSON object or array from a string that may
     * contain surrounding prose.
     */
    static String extractJson(String s) {
        // Find first { or [
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start;
        char openChar;
        char closeChar;
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart;
            openChar = '{';
            closeChar = '}';
        } else if (arrStart >= 0) {
            start = arrStart;
            openChar = '[';
            closeChar = ']';
        } else {
            return s;
        }

        // Find matching close bracket (respecting strings)
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == openChar) depth++;
            else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        // No matching close — return from start to end
        return s.substring(start);
    }

    /**
     * Repair common JSON formatting issues:
     * <ul>
     *   <li>Trailing commas before } or ]</li>
     *   <li>Single quotes -> double quotes</li>
     *   <li>Remove // line comments and /* block comments *\/</li>
     *   <li>Unquoted keys (basic heuristic)</li>
     * </ul>
     */
    static String repairJson(String json) {
        String s = json;

        // Remove single-line comments (not inside strings - heuristic)
        s = s.replaceAll("(?m)//[^\\n]*$", "");

        // Remove block comments (not inside strings - heuristic)
        s = s.replaceAll("/\\*[\\s\\S]*?\\*/", "");

        // Trailing commas before } or ]
        s = s.replaceAll(",\\s*([}\\]])", "$1");

        // Single quotes to double quotes (careful: only for JSON keys/values)
        // This is a heuristic and may not work for all cases
        s = s.replaceAll("'", "\"");

        // Unquoted keys: {key: value} -> {"key": value}
        // Match word characters immediately followed by colon
        Pattern unquotedKey = Pattern.compile("([{,])\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*:");
        Matcher m = unquotedKey.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + "\"" + m.group(2) + "\":");
        }
        m.appendTail(sb);
        s = sb.toString();

        return s.trim();
    }
}
