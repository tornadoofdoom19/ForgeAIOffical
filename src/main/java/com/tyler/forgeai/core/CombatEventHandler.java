package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CombatEventHandler: lightweight event hooks that can be wired to Minecraft events.
 * Methods here should be called from the mod's event callbacks (damage, teleport, item use).
 */
public class CombatEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-combat-events");

    private RewardSystem rewardSystem;
    private PunishmentSystem punishmentSystem;
    private TrainingManager trainingManager;

    // Shared singleton used by event hooks
    public static final CombatEventHandler INSTANCE = new CombatEventHandler();

    public interface CombatListener {
        void onCombatEvent(CombatEvent ev);
    }

    public static class CombatEvent {
        public final String attackerName;
        public final String defenderName;
        public final String winner; // either attacker or defender
        public final String module;
        public final long timestamp;

        public CombatEvent(String attackerName, String defenderName, String module, String winner) {
            this.attackerName = attackerName; this.defenderName = defenderName; this.module = module; this.winner = winner; this.timestamp = System.currentTimeMillis();
        }
    }

    private static final java.util.List<CombatListener> LISTENERS = new java.util.ArrayList<>();

    public static void registerListener(CombatListener listener) { if (listener != null) LISTENERS.add(listener); }
    private static void emitEvent(CombatEvent ev) { for (CombatListener l : LISTENERS) try { l.onCombatEvent(ev); } catch (Exception ignored) {} }

    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
    public void setPunishmentSystem(PunishmentSystem ps) { this.punishmentSystem = ps; }
    public void setTrainingManager(TrainingManager tm) { this.trainingManager = tm; }

    // Static helpers for event hooks to call
    public static void setGlobalRewardSystem(RewardSystem rs) { INSTANCE.setRewardSystem(rs); }
    public static void setGlobalPunishmentSystem(PunishmentSystem ps) { INSTANCE.setPunishmentSystem(ps); }
    public static void setGlobalTrainingManager(TrainingManager tm) { INSTANCE.setTrainingManager(tm); }

    public static void reportPlayerDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (INSTANCE != null) INSTANCE.observeEvent(player, "damage", amount);
    }

    public static void reportPlayerKilled(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
        if (INSTANCE != null) INSTANCE.onComboFailure(player, "Combat", "death");
    }

    public static void reportEnemyKilled(ServerPlayer player, net.minecraft.world.entity.LivingEntity victim) {
        if (INSTANCE != null) INSTANCE.onComboSuccess(player, "Combat", "kill");
    }

    /**
     * Call when a combo-related action succeeds (e.g., shield-disable landed, stun slam stunned opponent).
     */
    public void onComboSuccess(ServerPlayer player, String module, String comboName) {
        LOGGER.info("Combo success: {} -> {} by {}", module, comboName, player.getName().getString());
        if (trainingManager != null) trainingManager.recordSuccess(module + "." + comboName);
        if (rewardSystem != null) rewardSystem.reward(module + "." + comboName, 10);
        try { emitEvent(new CombatEvent(player.getName().getString(), "world", module + "." + comboName, player.getName().getString())); } catch (Exception ignored) {}
    }

    /**
     * Call when a combo attempt fails (e.g., failed pearl timing, died during attempt).
     */
    public void onComboFailure(ServerPlayer player, String module, String comboName) {
        LOGGER.warn("Combo failure: {} -> {} by {}", module, comboName, player.getName().getString());
        if (trainingManager != null) trainingManager.recordFailure(module + "." + comboName);
        if (punishmentSystem != null) punishmentSystem.punish(module + "." + comboName, 5);
        try { emitEvent(new CombatEvent(player.getName().getString(), "world", module + "." + comboName, "world")); } catch (Exception ignored) {}
    }

    /**
     * Call for general event observations (damage taken, teleport, shield broken).
     */
    public void observeEvent(ServerPlayer player, String eventName, float magnitude) {
        LOGGER.debug("Event observed: {} by {} (mag={})", eventName, player.getName().getString(), magnitude);
        // Simple heuristics: large self-damage after a combo attempt indicates failure
    }
}
