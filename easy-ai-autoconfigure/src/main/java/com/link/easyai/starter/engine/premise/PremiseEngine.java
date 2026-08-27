package com.link.easyai.starter.engine.premise;

import com.link.easyai.starter.engine.config.PremiseConfig;
import com.link.easyai.starter.engine.state.TaskState;

/**
 * Entry point for premise evaluation.
 * <p>
 * This is the interface that the engine calls; it delegates to a registered
 * PremiseEvaluator implementation.
 */
public interface PremiseEngine {

    /**
     * Evaluate whether a field's premise is satisfied.
     * If config is null, returns true (field always participates).
     *
     * @param config the premise configuration
     * @param state  the current task state
     * @return true if the field should participate in collection
     */
    boolean evaluate(PremiseConfig config, TaskState state);
}
