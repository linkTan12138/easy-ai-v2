package com.link.easyai.starter.engine.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.easyai.starter.config.LargeLanguageModelHolder;
import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.history.ChatMessage;
import com.link.easyai.starter.engine.llm.LlmCallException;
import com.link.easyai.starter.engine.llm.LlmClient;
import com.link.easyai.starter.engine.llm.RobustJsonParser;
import com.link.easyai.starter.engine.state.FieldState;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.service.LargeLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link ExtractionEngine}.
 * <p>
 * Pipeline per turn:
 * <ol>
 *   <li>{@link PromptBuilder} builds the system prompt from pending fields</li>
 *   <li>The LLM is called via {@link LlmClient} (retry + fallback + backoff)</li>
 *   <li>The raw response is parsed via {@link RobustJsonParser} (tolerates
 *       markdown fences, trailing commas, surrounding prose)</li>
 *   <li>Only fields that are actually pending this turn are kept — anything else
 *       the LLM returns is discarded (never trust LLM output blindly)</li>
 * </ol>
 */
@Component
public class DefaultExtractionEngine implements ExtractionEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultExtractionEngine.class);

    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;
    private final LargeLanguageModelHolder llmHolder;

    @Autowired
    public DefaultExtractionEngine(PromptBuilder promptBuilder,
                                    ObjectMapper objectMapper,
                                    LlmClient llmClient,
                                    LargeLanguageModelHolder llmHolder) {
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.llmClient = llmClient;
        this.llmHolder = llmHolder;
    }

    @Override
    public ExtractionResult extract(String userMessage,
                                    List<FieldDefinition> pendingFields,
                                    List<FieldDefinition> allFields,
                                    TaskState state,
                                    List<ChatMessage> chatHistory,
                                    LargeLanguageModel llm) {
        if (userMessage == null || userMessage.isBlank()) {
            return ExtractionResult.builder()
                    .success(true)
                    .fields(new HashMap<>())
                    .reason("empty user message")
                    .build();
        }

        // 1. Build system prompt from pending fields + chat history
        String systemPrompt = promptBuilder.build(pendingFields, allFields, state, chatHistory);

        // 2. Call LLM via resilient client (retry + fallback)
        String rawResponse;
        String primaryModel = llmHolder != null ? llmHolder.getActiveModelName() : "kimi";
        try {
            rawResponse = llmClient.chatCompletion(primaryModel, systemPrompt, userMessage);
        } catch (LlmCallException e) {
            log.error("[ExtractionEngine] LLM call failed after all retries/fallbacks: {}", e.getMessage());
            return ExtractionResult.fail("信息识别服务暂时不可用，请稍后重试", null);
        } catch (Exception e) {
            log.error("[ExtractionEngine] LLM call failed: {}", e.getMessage(), e);
            return ExtractionResult.fail("信息识别服务暂时不可用，请稍后重试", null);
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("[ExtractionEngine] LLM returned empty response");
            return ExtractionResult.fail("未能识别消息内容，请换种说法再试一次", rawResponse);
        }

        // 3. Parse JSON with robust parser (tolerates markdown fences, etc.)
        JsonNode root;
        try {
            root = RobustJsonParser.parse(rawResponse);
        } catch (Exception e) {
            log.warn("[ExtractionEngine] failed to parse LLM response as JSON: {} | raw={}",
                    e.getMessage(), rawResponse);
            return ExtractionResult.fail("未能识别消息内容，请换种说法再试一次", rawResponse);
        }

        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || !fieldsNode.isObject()) {
            // The LLM may occasionally answer with the fields object directly
            if (root.isObject()) {
                fieldsNode = root;
            } else {
                log.warn("[ExtractionEngine] LLM response has no 'fields' object: {}", rawResponse);
                return ExtractionResult.builder()
                        .success(true)
                        .fields(new HashMap<>())
                        .rawResponse(rawResponse)
                        .reason(textOrEmpty(root, "reason"))
                        .build();
            }
        }

        // 4. Keep only pending field codes, drop empty values.
        Set<String> acceptedCodes = new HashSet<>();
        for (FieldDefinition field : pendingFields) {
            acceptedCodes.add(field.getCode());
        }
        if (state != null && state.getFields() != null) {
            for (Map.Entry<String, FieldState> entry : state.getFields().entrySet()) {
                FieldState fieldState = entry.getValue();
                if (fieldState != null && fieldState.isCompleted() && fieldState.getValue() != null) {
                    acceptedCodes.add(entry.getKey());
                }
            }
        }

        Map<String, Object> extracted = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = fieldsNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String code = entry.getKey();
            if (!acceptedCodes.contains(code)) {
                log.debug("[ExtractionEngine] LLM returned non-pending field '{}', dropped", code);
                continue;
            }
            Object value = jsonNodeToValue(entry.getValue());
            if (isEmpty(value)) {
                continue;
            }
            extracted.put(code, value);
        }

        log.debug("[ExtractionEngine] extracted fields: {}", extracted.keySet());

        return ExtractionResult.builder()
                .success(true)
                .fields(extracted)
                .rawResponse(rawResponse)
                .reason(textOrEmpty(root, "reason"))
                .build();
    }

    private Object jsonNodeToValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isInt() || node.isLong() || node.isShort()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            java.util.List<Object> list = new java.util.ArrayList<>();
            for (JsonNode item : node) {
                Object v = jsonNodeToValue(item);
                if (!isEmpty(v)) {
                    list.add(v);
                }
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(e -> map.put(e.getKey(), jsonNodeToValue(e.getValue())));
            return map;
        }
        return node.toString();
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        if (value instanceof List<?> l) return l.isEmpty();
        if (value instanceof Map<?, ?> m) return m.isEmpty();
        return false;
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && n.isTextual() ? n.asText() : null;
    }
}
