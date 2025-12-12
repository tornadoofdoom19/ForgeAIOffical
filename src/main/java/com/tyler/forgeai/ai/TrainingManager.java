package com.tyler.forgeai.ai;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * TrainingManager handles adaptive learning:
 * - Tracks outcomes of module decisions
 * - Stores performance metrics
 * - Provides feedback loops for refinement
 */
public class TrainingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-training");

    // Simple metrics store (expandable later)
    private final Map<String, Integer> successCounts = new HashMap<>();
    private final Map<String, Integer> failureCounts = new HashMap<>();

    public void init() {
        LOGGER.info("TrainingManager initialized.");
    }

    /**
     * Record a successful action for a module.
     */
    public void recordSuccess(String moduleName) {
        successCounts.put(moduleName, successCounts.getOrDefault(moduleName, 0) + 1);
        LOGGER.debug("Recorded success for " + moduleName + " (total: " + successCounts.get(moduleName) + ")");
    }

    /**
     * Record a failed action for a module.
     */
    public void recordFailure(String moduleName) {
        failureCounts.put(moduleName, failureCounts.getOrDefault(moduleName, 0) + 1);
        LOGGER.debug("Recorded failure for " + moduleName + " (total: " + failureCounts.get(moduleName) + ")");
    }

    /**
     * Evaluate module performance.
     */
    public double getSuccessRate(String moduleName) {
        int successes = successCounts.getOrDefault(moduleName, 0);
        int failures = failureCounts.getOrDefault(moduleName, 0);
        int total = successes + failures;
        if (total == 0) return 0.0;
        return (double) successes / total;
    }

    /**
     * Suggest refinement based on performance.
     */
    public void suggestRefinement(String moduleName, Signals context) {
        double rate = getSuccessRate(moduleName);
        if (rate < 0.5) {
            LOGGER.info("Module " + moduleName + " underperforming (success rate " + rate + "). Consider refinement.");
            // Feed performance data to adaptive learning
            try {
                if (context != null && context.inCombat) {
                    LOGGER.debug("Low success rate in combat; adjusting " + moduleName + " priority lower.");
                }
            } catch (Exception e) {
                LOGGER.debug("Could not apply adaptive refinement: {}", e.getMessage());
            }
        } else {
            LOGGER.debug("Module " + moduleName + " performing adequately (success rate " + rate + ")");
        }
    }
}
