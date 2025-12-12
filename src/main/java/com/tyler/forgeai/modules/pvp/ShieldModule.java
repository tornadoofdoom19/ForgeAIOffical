package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.ai.RewardSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shield Module: Block all forms of damage (melee, projectiles, crystals).
 * Strategy:
 * - Use shield when blocking provides survival advantage
 * - Switch away from shield when lethal damage bypasses it
 * - Prioritize shield when opponent uses projectiles or crystals
 */
public class ShieldModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-shield");

    private boolean enabled = false;
    private boolean shieldActive = false;
    private boolean hasShield = false;
    private long lastShieldToggle = 0;
    private static final long SHIELD_TOGGLE_COOLDOWN = 100; // 0.1 seconds
    private RewardSystem rewardSystem;

    public void init() {
        LOGGER.info("ShieldModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || !hasShield || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Lethal burst damage incoming - switch away from shield
        if (signals.incomingMeleeDamage > signals.playerHealth && shieldActive && canToggleShield(now)) {
            disengageShield(player);
            lastShieldToggle = now;
            return;
        }

        // Crystal burst window - shield might not help
        if (signals.crystalOpportunity && shieldActive && canToggleShield(now)) {
            disengageShield(player);
            lastShieldToggle = now;
            return;
        }

        // Use shield against projectiles
        if (signals.inCombat() && signals.incomingProjectileDamage > 2.0f && !shieldActive && canToggleShield(now)) {
            engageShield(player);
            lastShieldToggle = now;
            return;
        }

        // Use shield against melee burst
        if (signals.inCombat() && signals.incomingMeleeDamage > 4.0f && !shieldActive && canToggleShield(now)) {
            engageShield(player);
            lastShieldToggle = now;
            return;
        }

        // Shield when opponent shielded (mirror them)
        if (signals.inCombat() && signals.opponentHasShield && !shieldActive && canToggleShield(now)) {
            engageShield(player);
            lastShieldToggle = now;
        }
    }

    private void engageShield(ServerPlayer player) {
        LOGGER.debug("Engaging shield for defense...");
        try {
            // Use off-hand item (shield) for brief hold
            com.tyler.forgeai.util.PlayerActionUtils.useItem(player, net.minecraft.world.InteractionHand.OFF_HAND, 10);
            shieldActive = true;
        } catch (Exception e) {
            LOGGER.debug("Error engaging shield: {}", e.getMessage());
            shieldActive = true;
        }
    }

    private void disengageShield(ServerPlayer player) {
        LOGGER.debug("Disengaging shield...");
        try {
            // Release off-hand use quickly
            com.tyler.forgeai.util.PlayerActionUtils.useItem(player, net.minecraft.world.InteractionHand.OFF_HAND, 1);
            shieldActive = false;
        } catch (Exception e) {
            LOGGER.debug("Error disengaging shield: {}", e.getMessage());
            shieldActive = false;
        }
    }

    private boolean canToggleShield(long now) {
        return (now - lastShieldToggle) >= SHIELD_TOGGLE_COOLDOWN && hasShield;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setHasShield(boolean has) { this.hasShield = has; }
    public boolean hasShield() { return hasShield; }
    public boolean isShieldActive() { return shieldActive; }
    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
}
