package com.link.easyai.starter.engine.premise;

import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Evaluates whether a field's premise (pre-condition) is satisfied.
 * <p>
 * First version supports a simple condition tree with operators:
 * exists, notExists, eq, neq, in.
 * <p>
 * Custom evaluators can be registered for business-specific conditions,
 * but the framework provides a default implementation that handles the basic operators.
 */
public interface PremiseEvaluator {

    /**
     * Evaluate the premise against the current task state.
     *
     * @param config the premise configuration (may be null = always true)
     * @param state  the current task state
     * @return true if the premise is satisfied (field should participate in collection)
     */
    boolean evaluate(PremiseConfig config, TaskState state);
}
