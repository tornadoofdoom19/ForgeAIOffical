package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import com.tyler.forgeai.util.PlayerActionUtils;
import com.tyler.forgeai.util.InventoryUtils;

/**
 * Potion Module: Detect available effects and use proactively/reactively.
 * Strategy:
 * - Proactive: Use strength before initiating combat
 * - Reactive: Use healing/resistance when threatened
 * - Learn which effects improve survival/aggression and bias toward effective usage
 * - Punish wasted potions
 */
public class PotionModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-potion");

    private boolean enabled = false;
    private final Map<String, Integer> availablePotions = new HashMap<>();
    private final Map<String, Long> potionCooldowns = new HashMap<>();
    private long lastPotionUsed = 0;
    private static final long POTION_COOLDOWN = 1500; // 1.5 seconds between potions
    
    // Effectiveness thresholds
    private static final float STRENGTH_THRESHOLD = 12.0f; // Use strength when healthy
    private static final float SPEED_THRESHOLD = 10.0f;     // Use speed when in combat
    private static final float HEALING_THRESHOLD = 6.0f;    // Use healing/regen when low
    private static final float FIRE_RES_THRESHOLD = 5.0f;   // Use fire res in lava

    public void init() {
        LOGGER.info("PotionModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || availablePotions.isEmpty() || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Emergency healing when low health
        if (signals.playerHealth < HEALING_THRESHOLD && hasPotion("Healing") && canUsePotion(now)) {
            usePotion(player, "Healing", signals);
            lastPotionUsed = now;
            return;
        }

        // Regeneration for sustained health management
        if (signals.playerHealth < 10.0f && signals.inCombat() && hasPotion("Regeneration") && canUsePotion(now)) {
            usePotion(player, "Regeneration", signals);
            lastPotionUsed = now;
            return;
        }

        // Fire resistance in lava
        if (signals.inLava && hasPotion("Fire Resistance") && canUsePotion(now)) {
            usePotion(player, "Fire Resistance", signals);
            lastPotionUsed = now;
            return;
        }

        // Strength before initiating combat
        if (!signals.inCombat() && signals.playerHealth > STRENGTH_THRESHOLD && 
            hasPotion("Strength") && canUsePotion(now)) {
            usePotion(player, "Strength", signals);
            lastPotionUsed = now;
            return;
        }

        // Speed for mobility during combat
        if (signals.inCombat() && signals.playerHealth > SPEED_THRESHOLD && 
            hasPotion("Speed") && canUsePotion(now)) {
            usePotion(player, "Speed", signals);
            lastPotionUsed = now;
            return;
        }

        // Resistance when taking heavy damage
        if (signals.inCombat() && signals.playerHealth < 12.0f && 
            hasPotion("Resistance") && canUsePotion(now)) {
            usePotion(player, "Resistance", signals);
            lastPotionUsed = now;
        }
    }

    private void usePotion(ServerPlayer player, String potionType, ContextScanner.Signals signals) {
        int count = availablePotions.getOrDefault(potionType, 0);
        if (count <= 0) return;
        LOGGER.debug("Using potion: {} (remaining: {})", potionType, count - 1);
        // Best-effort: move potion to hotbar, select and use main hand to consume
        try {
            boolean moved = InventoryUtils.moveItemToHotbar(player, potionType);
            if (moved) {
                // Simulate drinking time (typical potion duration ~32 ticks)
                PlayerActionUtils.useMainHand(player, 32);
            } else {
                // Fallback: attempt a short use
                PlayerActionUtils.useMainHand(player, 8);
            }
        } catch (Exception e) {
            LOGGER.debug("Error using potion {}: {}", potionType, e.getMessage());
        }
        availablePotions.put(potionType, count - 1);
    }

    private boolean hasPotion(String type) {
        return availablePotions.getOrDefault(type, 0) > 0;
    }

    private boolean canUsePotion(long now) {
        return (now - lastPotionUsed) >= POTION_COOLDOWN;
    }

    public void setPotionCount(String type, int count) {
        availablePotions.put(type, count);
    }

    public int getPotionCount(String type) {
        return availablePotions.getOrDefault(type, 0);
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
}
