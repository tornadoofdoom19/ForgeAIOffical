package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bow Module: Apply ranged pressure when melee is unsafe.
 * Strategy:
 * - Use bow when opponent is distant
 * - Switch to bow when low health (safer distance)
 * - Apply pressure while keeping distance
 */
public class BowModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-bow");

    private boolean enabled = false;
    private int arrowCount = 0;
    private boolean hasBow = false;
    private long lastArrowShot = 0;
    private static final long BOW_COOLDOWN = 600; // ~0.6 seconds per shot

    public void init() {
        LOGGER.info("BowModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || !hasBow || arrowCount == 0 || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Shoot when low health (safer distance)
        if (signals.inCombat() && signals.playerHealth < 8.0f && canShootArrow(now)) {
            shootArrowForSafety(player);
            lastArrowShot = now;
            return;
        }

        // Shoot when opponent at range
        if (signals.inCombat() && signals.opponentDistance > 8.0f && canShootArrow(now)) {
            shootArrowForPressure(player);
            lastArrowShot = now;
            return;
        }

        // Continuous pressure
        if (signals.inCombat() && canShootArrow(now)) {
            shootArrowForPressure(player);
            lastArrowShot = now;
        }
    }

    private void shootArrowForSafety(ServerPlayer player) {
        LOGGER.debug("Shooting arrow for safety...");
        try {
            // Look away from nearest opponent and shoot
            var opt = player.level().getNearestPlayer(player, 16);
            if (opt != null) {
                net.minecraft.world.phys.Vec3 dir = player.position().subtract(opt.position()).normalize();
                com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, player.getX() + dir.x, player.getY() + dir.y, player.getZ() + dir.z);
            }
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 10);
            arrowCount--;
        } catch (Exception e) {
            LOGGER.debug("Error shooting arrow for safety: {}", e.getMessage());
        }
    }

    private void shootArrowForPressure(ServerPlayer player) {
        LOGGER.debug("Shooting arrow for pressure...");
        try {
            var opt = player.level().getNearestPlayer(player, 32);
            if (opt != null) {
                com.tyler.forgeai.util.PlayerActionUtils.lookAtEntity(player, opt);
            }
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 15);
            arrowCount--;
        } catch (Exception e) {
            LOGGER.debug("Error shooting arrow for pressure: {}", e.getMessage());
        }
    }

    private boolean canShootArrow(long now) {
        return (now - lastArrowShot) >= BOW_COOLDOWN && arrowCount > 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setBow(boolean has) { this.hasBow = has; }
    public boolean hasBow() { return hasBow; }
    public void setArrowCount(int count) { this.arrowCount = count; }
    public int getArrowCount() { return arrowCount; }
}
