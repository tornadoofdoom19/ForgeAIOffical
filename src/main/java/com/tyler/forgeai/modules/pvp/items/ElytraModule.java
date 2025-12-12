package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.util.PlayerActionUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elytra Module: Aggressive aerial entry (paired with mace); defensive retreat; recovery and restock.
 * Strategy:
 * - Aggressive: Aerial entry for mace combo, height advantage over opponent
 * - Defensive: Retreat when overwhelmed, gain distance
 * - Recovery: Escape to restock armor/totems safely
 */
public class ElytraModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-elytra");

    private boolean enabled = false;
    private boolean hasElytra = false;
    private boolean elytraEquipped = false;
    private long lastElytraToggle = 0;
    private static final long ELYTRA_TOGGLE_COOLDOWN = 200; // 0.2 seconds

    public void init() {
        LOGGER.info("ElytraModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || !hasElytra || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // DEFENSIVE: Retreat when overwhelmed
        if (signals.inCombat() && signals.playerHealth < 6.0f && canToggleElytra(now)) {
            engageElytraForRetreat(player);
            lastElytraToggle = now;
            return;
        }

        // DEFENSIVE: Escape to restock/repair
        if (signals.isArmorBroken && !signals.inCombat() && canToggleElytra(now)) {
            engageElytraForRecover(player);
            lastElytraToggle = now;
            return;
        }

        // AGGRESSIVE: Aerial entry with mace (high health + opponent not airborne)
        if (signals.inCombat() && signals.playerHealth > 14.0f && 
            !signals.opponentAirborne && signals.hasMaceEquipped && canToggleElytra(now)) {
            engageElytraForAerialEntry(player);
            lastElytraToggle = now;
            return;
        }

        // AGGRESSIVE: Maintain aerial advantage
        if (signals.inCombat() && elytraEquipped && !signals.falling && canToggleElytra(now)) {
            // Continue circling for aerial combat
            maintainAerialAdvantage(player, signals);
        }
    }

    private void engageElytraForRetreat(ServerPlayer player) {
        LOGGER.debug("Engaging elytra for defensive retreat...");
        try {
            Entity opp = player.level().getNearestPlayer(player, 32);
            if (opp != null) {
                var dir = player.position().subtract(opp.position()).normalize();
                PlayerActionUtils.lookAt(player, player.getX() + dir.x * 4, player.getY() + dir.y * 2, player.getZ() + dir.z * 4);
            }
            PlayerActionUtils.jump(player);
            PlayerActionUtils.setSprinting(player, true);
            PlayerActionUtils.useMainHand(player, 5); // trigger elytra/firework use if available
            elytraEquipped = true;
        } catch (Exception e) {
            LOGGER.debug("Error engaging elytra for retreat: {}", e.getMessage());
            elytraEquipped = true;
        }
    }

    private void engageElytraForRecover(ServerPlayer player) {
        LOGGER.debug("Engaging elytra for recovery and restock...");
        try {
            // Simple safe location: move toward highest nearby safe Y
            double safeY = player.getY() + 20.0;
            PlayerActionUtils.lookAt(player, player.getX(), safeY, player.getZ());
            PlayerActionUtils.jump(player);
            PlayerActionUtils.useMainHand(player, 6);
            PlayerActionUtils.setSprinting(player, true);
            elytraEquipped = true;
        } catch (Exception e) {
            LOGGER.debug("Error engaging elytra for recover: {}", e.getMessage());
            elytraEquipped = true;
        }
    }

    private void engageElytraForAerialEntry(ServerPlayer player) {
        LOGGER.debug("Engaging elytra for aerial mace entry...");
        try {
            Entity opp = player.level().getNearestPlayer(player, 64);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(player, opp);
            }
            // Gain height then dive
            PlayerActionUtils.jump(player);
            PlayerActionUtils.useMainHand(player, 8);
            PlayerActionUtils.setSprinting(player, true);
            elytraEquipped = true;
        } catch (Exception e) {
            LOGGER.debug("Error engaging elytra for aerial entry: {}", e.getMessage());
            elytraEquipped = true;
        }
    }

    private void maintainAerialAdvantage(ServerPlayer player, ContextScanner.Signals signals) {
        LOGGER.debug("Maintaining aerial advantage...");
        try {
            Entity opp = player.level().getNearestPlayer(player, 64);
            if (opp != null) {
                // Circle: strafe while looking at opponent
                PlayerActionUtils.lookAtEntity(player, opp);
                PlayerActionUtils.moveInDirection(player, 0.2f, 0.6f);
                // If opportunity (opponent grounded and below), signal for mace strike via log
                if (!signals.opponentAirborne && player.getY() - opp.getY() > 1.5) {
                    LOGGER.debug("Aerial strike opportunity detected");
                }
            } else {
                // Stabilize flight
                PlayerActionUtils.moveForward(player, 0.4f);
            }
        } catch (Exception e) {
            LOGGER.debug("Error maintaining aerial advantage: {}", e.getMessage());
        }
    }

    private boolean canToggleElytra(long now) {
        return (now - lastElytraToggle) >= ELYTRA_TOGGLE_COOLDOWN && hasElytra;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setHasElytra(boolean has) { this.hasElytra = has; }
    public boolean hasElytra() { return hasElytra; }
    public boolean isElytraEquipped() { return elytraEquipped; }
}
