package com.tyler.forgeai.ai;

import com.tyler.forgeai.core.CombatEventHandler;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * CombatLearning: record combat events and provide simple summary metrics.
 */
public class CombatLearning {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-combat-learning");
    private final LearningStore store;

    public CombatLearning(LearningStore store) {
        this.store = store;
        try {
            CombatEventHandler.registerListener(ev -> onCombatEvent(ev));
        } catch (Exception ignored) {}
    }

    private void onCombatEvent(CombatEventHandler.CombatEvent ev) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("attacker", ev.attackerName);
            payload.put("defender", ev.defenderName);
            payload.put("outcome", ev.winner);
            payload.put("module", ev.module);
            String key = "combat-" + (ev.attackerName == null ? "unknown" : ev.attackerName);
            store.record(key, payload);
        } catch (Exception e) {
            LOGGER.debug("CombatLearning record error: {}", e.getMessage());
        }
    }

    public Map<String, Integer> getCombatSummary(ServerPlayer player) {
        if (player == null) return Map.of();
        return store.summarizeCounts("combat-" + player.getGameProfile().getName(), "outcome");
    }
}
