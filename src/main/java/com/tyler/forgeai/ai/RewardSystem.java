package com.tyler.forgeai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RewardSystem reinforces positive outcomes:
 * - Grants reward points to modules
 * - Encourages repeating successful strategies
 * - Can integrate with TrainingManager and MemoryManager
 */
public class RewardSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-reward");

    private int rewardPoints = 0;

    public void init() {
        LOGGER.info("RewardSystem initialized.");
    }

    /**
     * Apply a reward for a successful action.
     */
    public void reward(String moduleName, int points) {
        rewardPoints += points;
        LOGGER.info("Rewarded " + points + " points to " + moduleName +
                " (total rewards: " + rewardPoints + ")");
        // TODO: feed into TrainingManager or MemoryManager for reinforcement
    }

    /**
     * Get current reward total.
     */
    public int getRewardPoints() {
        return rewardPoints;
    }

    /**
     * Reset rewards (e.g., new session).
     */
    public void reset() {
        rewardPoints = 0;
        LOGGER.info("Reward points reset.");
    }
}
