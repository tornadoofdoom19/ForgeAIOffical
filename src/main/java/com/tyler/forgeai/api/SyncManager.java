package com.tyler.forgeai.api;

import com.tyler.forgeai.core.DecisionEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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
    public void broadcastMode(MinecraftServer server) {
        String mode = getCurrentMode();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(() -> "ForgeAI mode: " + mode, false);
        }
        LOGGER.debug("Broadcasted mode: " + mode);
    }

    /**
     * Sync AI state with a specific player.
     */
    public void syncWithPlayer(ServerPlayerEntity player) {
        String mode = getCurrentMode();
        player.sendMessage(() -> "ForgeAI synced mode: " + mode, false);
        LOGGER.debug("Synced mode with player: " + player.getName().getString());
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
