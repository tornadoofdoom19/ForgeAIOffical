package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.ai.RewardSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Totem Module: Use totem to prevent death when lethal damage is imminent.
 * Strategy:
 * - Hold totem in offhand
 * - Trigger when lethal damage is inevitable
 * - Learn correct timing to avoid wasting totems
 */
public class TotemModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-totem");

    private boolean enabled = false;
    private int totemCount = 0;
    private long lastTotemUse = 0;
    private static final long TOTEM_COOLDOWN = 3000; // 3 seconds (totem has cooldown)
    private RewardSystem rewardSystem;
    private static final float LETHAL_THRESHOLD = 0.5f; // Trigger when health < 0.5 hearts

    public void init() {
        LOGGER.info("TotemModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || totemCount == 0 || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Lethal damage incoming - use totem
        if (signals.incomingMeleeDamage >= signals.playerHealth && canUseTotem(now)) {
            useTotem(player, signals);
            lastTotemUse = now;
            return;
        }

        // Burst damage incoming that exceeds shield - use totem
        if (signals.inCombat() && signals.playerHealth < 4.0f && 
            signals.incomingMeleeDamage + signals.incomingProjectileDamage > signals.playerHealth && 
            canUseTotem(now)) {
            useTotem(player, signals);
            lastTotemUse = now;
            return;
        }

        // Last resort: health critically low
        if (signals.playerHealth < LETHAL_THRESHOLD && signals.inCombat() && canUseTotem(now)) {
            useTotem(player, signals);
            lastTotemUse = now;
        }
    }

    private void useTotem(ServerPlayer player, ContextScanner.Signals signals) {
        LOGGER.debug("Using totem! (Health: {}, Incoming: {})", signals.playerHealth, signals.incomingMeleeDamage);
        try {
            // Best-effort: move totem to hotbar and attempt a quick use sequence
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "totem");
            // Attempt to use main hand as quick fallback; vanilla triggers totem if in offhand on death
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 2);
        } catch (Exception e) {
            LOGGER.debug("Error triggering totem: {}", e.getMessage());
        }
        totemCount--;

        // Reward correct totem usage
        if (rewardSystem != null && signals.playerHealth < 4.0f) {
            rewardSystem.reward("TotemModule", 75);
        }
    }

    private boolean canUseTotem(long now) {
        return (now - lastTotemUse) >= TOTEM_COOLDOWN && totemCount > 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setTotemCount(int count) { this.totemCount = count; }
    public int getTotemCount() { return totemCount; }
    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
}
