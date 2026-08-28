package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.history.ChatMessage;
import com.link.easyai.starter.engine.state.TaskState;
import com.link.easyai.starter.service.LargeLanguageModel;

import java.util.List;

/**
 * Orchestrates the LLM extraction step:
 * 1. Use PromptBuilder to build system prompt from pending fields
 * 2. Call LLM with user message
 * 3. Parse response into ExtractionResult
 * <p>
 * This engine does NOT validate or normalize — it only extracts.
 */
public interface ExtractionEngine {

    /**
     * Extract field values from user input.
     *
     * @param userMessage    the user's latest message
     * @param pendingFields  fields that need collection this turn
     * @param allFields      the full field definition set of the task (may be null);
     *                       forwarded to the prompt builder so the already-collected
     *                       summary carries field semantics (name/description)
     * @param state          current task state (for context)
     * @param chatHistory    conversation history sliding window, for multi-turn
     *                       context understanding. May be empty.
     * @param llm            the LLM to call
     * @return extraction result
     */
    ExtractionResult extract(String userMessage,
                              List<FieldDefinition> pendingFields,
                              List<FieldDefinition> allFields,
                              TaskState state,
                              List<ChatMessage> chatHistory,
                              LargeLanguageModel llm);
}
