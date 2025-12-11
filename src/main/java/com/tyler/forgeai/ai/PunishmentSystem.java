package com.tyler.forgeai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PunishmentSystem discourages negative outcomes:
 * - Applies penalties to modules
 * - Helps AI avoid repeating failed strategies
 * - Can integrate with TrainingManager and MemoryManager
 */
public class PunishmentSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-punish");

    private int penaltyPoints = 0;

    public void init() {
        LOGGER.info("PunishmentSystem initialized.");
    }

    /**
     * Apply a penalty for a failed action.
     */
    public void punish(String moduleName, int points) {
        penaltyPoints += points;
        LOGGER.warn("Applied " + points + " penalty points to " + moduleName +
                " (total penalties: " + penaltyPoints + ")");
        // TODO: feed into TrainingManager or MemoryManager for corrective learning
    }

    /**
     * Get current penalty total.
     */
    public int getPenaltyPoints() {
        return penaltyPoints;
    }

    /**
     * Reset penalties (e.g., new session).
     */
    public void reset() {
        penaltyPoints = 0;
        LOGGER.info("Penalty points reset.");
    }
}
