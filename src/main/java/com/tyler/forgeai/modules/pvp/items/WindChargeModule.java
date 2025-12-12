package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wind Charge Module: Use for knockback, aerial repositioning, or disengagement.
 * Strategy:
 * - Knockback opponent when cornered
 * - Reposition when opponent has height advantage
 * - Disengage when health critical and opponent too strong
 */
public class WindChargeModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-windcharge");

    private boolean enabled = false;
    private int windChargeCount = 0;
    private long lastWindCharge = 0;
    private static final long WIND_CHARGE_COOLDOWN = 400; // ~0.4 seconds

    public void init() {
        LOGGER.info("WindChargeModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Critical disengage: use wind charge to escape
        if (signals.playerHealth < 4.0f && !signals.hasItem("totem") && canUseWindCharge(now)) {
            useWindChargeToEscape(player, signals);
            lastWindCharge = now;
            return;
        }

        // Reposition if opponent airborne (gain height advantage)
        if (signals.inCombat() && signals.opponentAirborne && canUseWindCharge(now)) {
            useWindChargeToReposition(player);
            lastWindCharge = now;
            return;
        }

        // Knockback when cornered
        if (signals.inCombat() && canUseWindCharge(now)) {
            useWindChargeForKnockback(player);
            lastWindCharge = now;
        }
    }

    private void useWindChargeToEscape(ServerPlayer player, ContextScanner.Signals signals) {
        LOGGER.debug("Using wind charge to escape (health: {})", signals.playerHealth);
        try {
            // Push player backward and upward relative to look direction
            net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0F);
            net.minecraft.world.phys.Vec3 push = look.scale(-0.8).add(0, 0.6, 0);
            player.setDeltaMovement(push);
            com.tyler.forgeai.util.PlayerActionUtils.jump(player);
            windChargeCount--;
        } catch (Exception e) {
            LOGGER.debug("Error using wind charge escape: {}", e.getMessage());
        }
    }

    private void useWindChargeToReposition(ServerPlayer player) {
        LOGGER.debug("Using wind charge to reposition...");
        try {
            player.setDeltaMovement(new net.minecraft.world.phys.Vec3(0, 0.9, 0));
            com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, player.getX(), player.getY() + 10, player.getZ());
            windChargeCount--;
        } catch (Exception e) {
            LOGGER.debug("Error using wind charge reposition: {}", e.getMessage());
        }
    }

    private void useWindChargeForKnockback(ServerPlayer player) {
        LOGGER.debug("Using wind charge for knockback...");
        try {
            // Push nearby opponents away
            double range = 5.0;
            var entities = player.level().getEntities(player, player.getBoundingBox().inflate(range), e -> e instanceof net.minecraft.world.entity.LivingEntity && e != player);
            for (var ent : entities) {
                if (ent instanceof net.minecraft.world.entity.LivingEntity) {
                    net.minecraft.world.entity.LivingEntity le = (net.minecraft.world.entity.LivingEntity) ent;
                    net.minecraft.world.phys.Vec3 dir = le.position().subtract(player.position()).normalize();
                    le.push(dir.x * 1.2, 0.5, dir.z * 1.2);
                }
            }
            windChargeCount--;
        } catch (Exception e) {
            LOGGER.debug("Error using wind charge knockback: {}", e.getMessage());
        }
    }

    private boolean canUseWindCharge(long now) {
        return (now - lastWindCharge) >= WIND_CHARGE_COOLDOWN && windChargeCount > 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setWindChargeCount(int count) { this.windChargeCount = count; }
    public int getWindChargeCount() { return windChargeCount; }
}
