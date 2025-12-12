package com.tyler.forgeai.modules.combat;

import com.tyler.forgeai.ai.LearningStore;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.CombatEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public class CombatLearning {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-combat-learn");
    private final LearningStore store;
    private final TrainingManager trainingManager;

    public CombatLearning(LearningStore store, TrainingManager tm) {
        this.store = store;
        this.trainingManager = tm;
    }

    public void init() {
        LOGGER.info("CombatLearning initialized");
        CombatEventHandler.registerListener(this::onCombatEvent);
    }

    private void onCombatEvent(CombatEventHandler.CombatEvent ev) {
        // Record basic outcome: attacker vs defender, damage, success
        try {
            String key = String.format("combat:%s_vs_%s", ev.attackerName, ev.defenderName);
            Map<String, Object> summary = new HashMap<>();
            summary.put("lastOutcome", ev.winner);
            summary.put("timestamp", System.currentTimeMillis());
            store.put(key, summary);
            if (trainingManager != null && ev.winner != null) {
                // Update success metrics
                trainingManager.recordOutcome(ev.module, ev.winner.equals(ev.attackerName));
            }
        } catch (Exception e) { LOGGER.debug("onCombatEvent error: {}", e.getMessage()); }
    }
}
