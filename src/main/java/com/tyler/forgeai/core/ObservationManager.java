package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.LearningStore;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * ObservationManager: records observations (movement, inventory choices, fights) to LearningStore.
 * Enhanced to monitor player behavior for learning and mimicry.
 */
public class ObservationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-observations");
    private final LearningStore store;
    private boolean observationMode = false;
    private ServerPlayer observedPlayer = null;

    public ObservationManager(LearningStore store) {
        this.store = store;
    }

    public void recordMovement(ServerPlayer player, String style, double speed) {
        Map<String, Object> obs = new HashMap<>();
        obs.put("type", "movement");
        obs.put("style", style);
        obs.put("speed", speed);
        obs.put("timestamp", System.currentTimeMillis());
        try { store.record("observation-" + player.getGameProfile().getName(), obs); } catch (Exception e) { LOGGER.debug("recordMovement error: {}", e.getMessage()); }
    }

    public void recordInventoryChoice(ServerPlayer player, Map<String, Object> details) {
        details.put("timestamp", System.currentTimeMillis());
        try { store.record("inventory-" + player.getGameProfile().getName(), details); } catch (Exception e) { LOGGER.debug("recordInventoryChoice error: {}", e.getMessage()); }
    }

    public void recordCombatObservation(ServerPlayer player, Map<String, Object> details) {
        details.put("timestamp", System.currentTimeMillis());
        try { store.record("combat-" + player.getGameProfile().getName(), details); } catch (Exception e) { LOGGER.debug("recordCombatObservation error: {}", e.getMessage()); }
    }

    /**
     * Record building actions for learning.
     */
    public void recordBuildingAction(ServerPlayer player, String action, String blockType, int x, int y, int z) {
        Map<String, Object> obs = new HashMap<>();
        obs.put("type", "building");
        obs.put("action", action);
        obs.put("blockType", blockType);
        obs.put("position", x + "," + y + "," + z);
        obs.put("timestamp", System.currentTimeMillis());
        try { store.record("building-" + player.getGameProfile().getName(), obs); } catch (Exception e) { LOGGER.debug("recordBuildingAction error: {}", e.getMessage()); }
    }

    /**
     * Record task performance for learning.
     */
    public void recordTaskObservation(ServerPlayer player, String taskType, boolean success, long duration) {
        Map<String, Object> obs = new HashMap<>();
        obs.put("type", "task");
        obs.put("taskType", taskType);
        obs.put("success", success);
        obs.put("duration", duration);
        obs.put("timestamp", System.currentTimeMillis());
        try { store.record("task-" + player.getGameProfile().getName(), obs); } catch (Exception e) { LOGGER.debug("recordTaskObservation error: {}", e.getMessage()); }
    }

    /**
     * Start observing a specific player.
     */
    public void startObserving(ServerPlayer player) {
        observationMode = true;
        observedPlayer = player;
        LOGGER.info("Started observing player: {}", player.getGameProfile().getName());
    }

    /**
     * Stop observing.
     */
    public void stopObserving() {
        observationMode = false;
        observedPlayer = null;
        LOGGER.info("Stopped observing");
    }

    /**
     * Check if currently observing a player.
     */
    public boolean isObserving() {
        return observationMode && observedPlayer != null;
    }

    /**
     * Get the currently observed player.
     */
    public ServerPlayer getObservedPlayer() {
        return observedPlayer;
    }

    /**
     * Analyze observed behavior and suggest improvements.
     */
    public String analyzeObservedBehavior() {
        if (!isObserving() || observedPlayer == null) {
            return "Not currently observing any player.";
        }

        // This would analyze stored observations and provide insights
        // For now, return a placeholder
        return "Analysis: Player " + observedPlayer.getGameProfile().getName() + " shows efficient combat patterns.";
    }
}
