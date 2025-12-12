package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.LearningStore;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * ObservationManager: records observations (movement, inventory choices, fights) to LearningStore.
 */
public class ObservationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-observations");
    private final LearningStore store;

    public ObservationManager(LearningStore store) {
        this.store = store;
    }

    public void recordMovement(ServerPlayer player, String style, double speed) {
        Map<String, Object> obs = new HashMap<>();
        obs.put("type", "movement");
        obs.put("style", style);
        obs.put("speed", speed);
        try { store.record("observation-" + player.getGameProfile().getName(), obs); } catch (Exception e) { LOGGER.debug("recordMovement error: {}", e.getMessage()); }
    }

    public void recordInventoryChoice(ServerPlayer player, Map<String, Object> details) {
        try { store.record("inventory-" + player.getGameProfile().getName(), details); } catch (Exception e) { LOGGER.debug("recordInventoryChoice error: {}", e.getMessage()); }
    }

    public void recordCombatObservation(ServerPlayer player, Map<String, Object> details) {
        try { store.record("combat-" + player.getGameProfile().getName(), details); } catch (Exception e) { LOGGER.debug("recordCombatObservation error: {}", e.getMessage()); }
    }
}
