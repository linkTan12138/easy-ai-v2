package com.link.easyai.starter.engine;

import com.link.easyai.starter.engine.context.TaskContext;

/**
 * The heart of the AI Task Engine.
 * <p>
 * Business code calls this engine with a task type and user message.
 * The engine handles the entire flow:
 * <pre>
 * load config
 *   → load task state
 *   → select pending fields (premise filtering)
 *   → build prompt
 *   → call LLM
 *   → parse ExtractionResult
 *   → validate fields (validator pipeline)
 *   → normalize values
 *   → assemble FieldValues (mapping)
 *   → update TaskState
 *   → check completion
 *   → if incomplete → save state, return "need more" response
 *   → if complete → execute Action → execute PostActions → save state
 *   → return response
 * </pre>
 * <p>
 * The engine does NOT know about any specific business scenario.
 * All scenario-specific behavior is driven by configuration and pluggable
 * Validator/Action/PostAction implementations.
 */
public interface AiTaskEngine {

    /**
     * Process a single conversation turn for a task.
     *
     * @param taskType    the task type, e.g. "ORDER_UPDATE"
     * @param taskId      the task ID (unique per conversation session)
     * @param userMessage the user's latest message
     * @param taskContext the task context (tenant, user, shared data)
     * @return engine response (either "need more info" or "task complete")
     */
    AiTaskResponse execute(String taskType, String taskId, String userMessage, TaskContext taskContext);
}
