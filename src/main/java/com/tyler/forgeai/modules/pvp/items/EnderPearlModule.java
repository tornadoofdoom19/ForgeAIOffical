package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ender Pearl Module: Aggressive gap-close, flanking, crystal entry; defensive escape or restock.
 * Strategy:
 * - Aggressive: Gap-close, flank, or entry for crystal burst
 * - Defensive: Escape when health critical, restock at ender chest
 * - Resume fights after recovery
 */
public class EnderPearlModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-enderpearl");

    private boolean enabled = false;
    private int pearlCount = 0;
    private long lastPearl = 0;
    private static final long PEARL_COOLDOWN = 500; // 0.5 seconds
    private static final float AGGRESSIVE_THRESHOLD = 12.0f;
    private static final float DEFENSIVE_THRESHOLD = 4.0f;

    public void init() {
        LOGGER.info("EnderPearlModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || pearlCount == 0 || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // DEFENSIVE: Escape when health critical
        if (signals.playerHealth < DEFENSIVE_THRESHOLD && canThrowPearl(now)) {
            throwPearlToEscape(player, signals);
            lastPearl = now;
            return;
        }

        // DEFENSIVE: Escape to restock/repair
        if (signals.isArmorBroken && !signals.inCombat() && canThrowPearl(now)) {
            throwPearlToRestock(player);
            lastPearl = now;
            return;
        }

        // AGGRESSIVE: Gap-close when winning
        if (signals.inCombat() && signals.playerHealth > AGGRESSIVE_THRESHOLD && 
            signals.opponentDistance > 5.0f && canThrowPearl(now)) {
            throwPearlToGapClose(player);
            lastPearl = now;
            return;
        }

        // AGGRESSIVE: Flank for crystal entry
        if (signals.inCombat() && signals.crystalOpportunity && canThrowPearl(now)) {
            throwPearlForCrystalEntry(player);
            lastPearl = now;
        }
    }

    private void throwPearlToEscape(ServerPlayer player, ContextScanner.Signals signals) {
        LOGGER.debug("Throwing pearl to escape (health: {})", signals.playerHealth);
        try {
            var opt = player.level().getNearestPlayer(player, 32);
            net.minecraft.world.phys.Vec3 dir = net.minecraft.world.phys.Vec3.ZERO;
            if (opt != null) dir = player.position().subtract(opt.position()).normalize();
            net.minecraft.world.phys.Vec3 target = player.position().add(dir.scale(8));
            com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, target.x, target.y, target.z);
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 5);
            pearlCount--;
        } catch (Exception e) {
            LOGGER.debug("Error throwing pearl to escape: {}", e.getMessage());
        }
    }

    private void throwPearlToRestock(ServerPlayer player) {
        LOGGER.debug("Throwing pearl to navigate for restock...");
        try {
            // Simple fallback: throw pearl forward to move away and search for chest
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 5);
            pearlCount--;
        } catch (Exception e) {
            LOGGER.debug("Error throwing pearl to restock: {}", e.getMessage());
        }
    }

    private void throwPearlToGapClose(ServerPlayer player) {
        LOGGER.debug("Throwing pearl to gap-close...");
        try {
            var opt = player.level().getNearestPlayer(player, 64);
            if (opt != null) {
                com.tyler.forgeai.util.PlayerActionUtils.lookAtEntity(player, opt);
                com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 5);
            }
            pearlCount--;
        } catch (Exception e) {
            LOGGER.debug("Error throwing pearl to gap-close: {}", e.getMessage());
        }
    }

    private void throwPearlForCrystalEntry(ServerPlayer player) {
        LOGGER.debug("Throwing pearl for crystal entry...");
        try {
            // Throw pearl slightly toward player's look direction for aggression
            var look = player.getViewVector(1.0F);
            com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, player.getX() + look.x * 4, player.getY() + look.y * 2, player.getZ() + look.z * 4);
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 5);
            pearlCount--;
        } catch (Exception e) {
            LOGGER.debug("Error throwing pearl for crystal entry: {}", e.getMessage());
        }
    }

    private boolean canThrowPearl(long now) {
        return (now - lastPearl) >= PEARL_COOLDOWN && pearlCount > 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setPearlCount(int count) { this.pearlCount = count; }
    public int getPearlCount() { return pearlCount; }
}
