package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fishing Rod Module: Pull opponents out of position or break shields.
 * Strategy:
 * - Use against shielded opponents (ignore shield)
 * - Pull opponents into disadvantageous positions
 * - Interrupt opponent combos with hook
 */
public class FishingRodModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-fishingrod");

    private boolean enabled = false;
    private boolean hasFishingRod = false;
    private long lastHook = 0;
    private static final long HOOK_COOLDOWN = 1000; // 1 second

    public void init() {
        LOGGER.info("FishingRodModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || !hasFishingRod || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Hook shielded opponent (bypasses shield)
        if (signals.inCombat() && signals.opponentHasShield && canHook(now)) {
            hookOpponent(player);
            lastHook = now;
            return;
        }

        // Pull opponent out of position
        if (signals.inCombat() && canHook(now)) {
            hookOpponent(player);
            lastHook = now;
        }
    }

    private void hookOpponent(ServerPlayer player) {
        LOGGER.debug("Hooking opponent with fishing rod...");
        try {
            // Use fishing rod item (simulate cast)
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 10);
            // Slight pull toward player to simulate hook effect
            var entities = player.level().getEntities(player, player.getBoundingBox().inflate(6), e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player);
            for (var ent : entities) {
                if (ent instanceof net.minecraft.world.entity.LivingEntity) {
                    net.minecraft.world.entity.LivingEntity le = (net.minecraft.world.entity.LivingEntity) ent;
                    net.minecraft.world.phys.Vec3 dir = player.position().subtract(le.position()).normalize();
                    le.push(dir.x * 0.6, 0.2, dir.z * 0.6);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error hooking opponent: {}", e.getMessage());
        }
    }

    private boolean canHook(long now) {
        return (now - lastHook) >= HOOK_COOLDOWN && hasFishingRod;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setHasFishingRod(boolean has) { this.hasFishingRod = has; }
    public boolean hasFishingRod() { return hasFishingRod; }
}
