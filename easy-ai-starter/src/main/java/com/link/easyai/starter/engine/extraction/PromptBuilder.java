package com.link.easyai.starter.engine.extraction;

import com.link.easyai.starter.engine.config.FieldDefinition;
import com.link.easyai.starter.engine.state.TaskState;

import java.util.List;

/**
 * Builds the system prompt for LLM extraction based on pending fields.
 * <p>
 * Replaces the old hardcoded SYS_PROMPT in {@link com.link.easyai.starter.service.AiSceneProcessor}.
 * Only sends fields that are currently pending (not yet collected), rather than
 * all field definitions at once.
 */
public interface PromptBuilder {

    /**
     * Build the system prompt for the current extraction round.
     *
     * @param pendingFields the fields that need collection this turn
     * @param allFields     the full field definition set of the task (may be null).
     *                      Used to enrich the already-collected summary with each
     *                      field's name / description, so the LLM can recognize
     *                      re-provided values as corrections even when the user
     *                      refers to a field by name, alias or meaning (e.g.
     *                      "订单号" for 客户单号). When null, the summary falls
     *                      back to plain "code=value" lines.
     * @param state         the current task state (for context: already collected values)
     * @return the system prompt string
     */
    String build(List<FieldDefinition> pendingFields,
                 List<FieldDefinition> allFields,
                 TaskState state);
}
