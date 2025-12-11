package com.tyler.forgeai.api;

import com.tyler.forgeai.core.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SyncManager ensures server-client synchronization for ForgeAI:
 * - Broadcasts mode changes
 * - Keeps module states consistent
 * - Provides hooks for multiplayer sync
 */
public class SyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-sync");

    private final DecisionEngine decisionEngine;

    public SyncManager(DecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    public void init() {
        LOGGER.info("SyncManager initialized.");
    }

    /**
     * Broadcast current AI mode to all players.
     */
    public void broadcastMode(Object server) {
        String mode = getCurrentMode();
        // TODO: Implement proper messaging API for Minecraft 1.21.8
        LOGGER.debug("Broadcasted mode: " + mode);
    }

    /**
     * Sync AI state with a specific player.
     */
    public void syncWithPlayer(Object player) {
        String mode = getCurrentMode();
        // TODO: Implement proper player messaging
        LOGGER.debug("Synced mode with player: " + mode);
    }

    /**
     * Get current mode string for display.
     */
    private String getCurrentMode() {
        if (decisionEngine.isCombatMode()) return "Combat";
        if (decisionEngine.isBuilderMode()) return "Builder";
        if (decisionEngine.isGathererMode()) return "Gatherer";
        if (decisionEngine.isStasisMode()) return "Stasis";
        return "Unknown";
    }
}
