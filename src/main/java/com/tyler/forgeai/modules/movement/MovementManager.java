package com.tyler.forgeai.modules.movement;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/** MovementManager: Handles human-like movement with randomness, strafing, dodge, elytra, and riptide control. */
public class MovementManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-movement");
    private final Random rng = new Random();
    private boolean active = true;

    public MovementManager() {}

    public void init() { LOGGER.info("MovementManager initialized"); }

    public void setActive(boolean enabled) { active = enabled; }

    public void tick(ContextScanner.Signals s) {
        if (!active || s == null || s.player == null) return;
        try {
            if (rng.nextInt(10) == 0) {
                double yawJitter = (rng.nextDouble() - 0.5) * 6.0; // +/- 3 degrees
                double pitchJitter = (rng.nextDouble() - 0.5) * 2.0;
                com.tyler.forgeai.util.PlayerActionUtils.adjustLook(s.player, (float) yawJitter, (float) pitchJitter);
            }
            if (s.isFlyingWithElytra()) {
                if (rng.nextInt(10) < 3) {
                    com.tyler.forgeai.util.PlayerActionUtils.adjustLook(s.player, (float) ((rng.nextDouble() - 0.5) * 12.0), 0f);
                }
            }
        } catch (Exception e) { LOGGER.debug("Movement tick error: {}", e.getMessage()); }
    }

    public void lookAt(ServerPlayer player, BlockPos pos) {
        try { com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, pos); } catch (Exception ignored) {}
    }

    public void moveTo(ServerPlayer player, BlockPos pos) {
        try { com.tyler.forgeai.util.PlayerActionUtils.navigateTo(player, pos); } catch (Exception e) { LOGGER.warn("moveTo failed: {}", e.getMessage()); }
    }

    public void strafe(ServerPlayer player, float strength) { try { com.tyler.forgeai.util.PlayerActionUtils.strafe(player, strength); } catch (Exception e) { } }

    public void dodge(ServerPlayer player) { try { com.tyler.forgeai.util.PlayerActionUtils.dodge(player); } catch (Exception e) { } }

    public void startElytra(ServerPlayer player) { try { com.tyler.forgeai.util.PlayerActionUtils.useElytra(player); } catch (Exception e) { } }

    public void stopElytra(ServerPlayer player) { try { com.tyler.forgeai.util.PlayerActionUtils.cancelElytra(player); } catch (Exception e) { } }

    public void elytraFlyTo(ServerPlayer player, BlockPos target) {
        try {
            com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, target.getX(), target.getY(), target.getZ());
            com.tyler.forgeai.util.PlayerActionUtils.setSprinting(player, true);
        } catch (Exception ignored) {}
    }

    public void riptideTravel(ServerPlayer player, BlockPos target) {
        try {
            com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, target.getX(), target.getY(), target.getZ());
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 10);
        } catch (Exception ignored) {}
    }
}
