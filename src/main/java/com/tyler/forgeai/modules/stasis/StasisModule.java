package com.tyler.forgeai.modules.stasis;

import com.tyler.forgeai.core.ContextScanner.Signals;
import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.core.TaskManager;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.TrapDoorBlock;
import com.tyler.forgeai.util.BlockInteractionUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StasisModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-stasis");

    private boolean active = false;
    private final Map<UUID, PearlInfo> pearlMap = new HashMap<>();
    private PunishmentSystem punishmentSystem = null;

    public void init() {
        LOGGER.info("Stasis module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Stasis module active: " + enabled);
    }

    public void setPunishmentSystem(PunishmentSystem ps) { this.punishmentSystem = ps; }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Stasis tick running for player: " + s.player.getName().getString());

        // Continuously scan for ender pearls and map them to their owner + nearest trapdoor
        try { scanForPearls(s.player, 32); } catch (Exception ignored) {}

        if (s.shouldEnterStasis()) {
            enterStasis(s);
        } else {
            LOGGER.debug("Conditions not met — StasisModule idle.");
        }
    }

    private void enterStasis(Signals s) {
        try {
            var player = s.player;
            if (player == null) return;

            // Stop moving and conserve energy: crouch and reduce sprint
            com.tyler.forgeai.util.PlayerActionUtils.setSprinting(player, false);
            com.tyler.forgeai.util.PlayerActionUtils.setCrouching(player, true);

            // Auto-eat if low health
            if (s.playerHealth < 10.0f) {
                try { com.tyler.forgeai.util.FoodUtils.autoEatIfLow(player, 10.0f); } catch (Exception ignored) {}
            }

            // Monitor and heal if applicable (potions, regen)
            LOGGER.info("Stasis mode: conserving resources for {}", player.getName().getString());
            // Attempt to pull a pearl for the player (self-protection), if available
            try { attemptPullForPlayer(player, player); } catch (Exception ignored) {}
        } catch (Exception e) {
            LOGGER.debug("enterStasis error: {}", e.getMessage());
        }
        LOGGER.info("Entering stasis mode — AI is idle and conserving resources.");
        // Example: stop movement, monitor environment, auto-heal if possible, prepare for next action
    }

    /**
     * Scan nearby area for thrown ender pearls and map them to owner with nearest trapdoor.
     */
    public void scanForPearls(ServerPlayer player, int radius) {
        if (player == null) return;
        Level level = player.level();
        List<ThrownEnderpearl> pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, player.getBoundingBox().inflate(radius));
        long now = System.currentTimeMillis();
        for (ThrownEnderPearl pearl : pearls) {
            Entity owner = pearl.getOwner();
            if (owner == null || !(owner instanceof ServerPlayer)) continue;
            ServerPlayer ownerPlayer = (ServerPlayer) owner;
            UUID ownerId = ownerPlayer.getUUID();
            BlockPos pearlPos = pearl.blockPosition();
            // Find nearest trapdoor to the pearl
            BlockPos trapdoor = findNearestTrapdoor(level, pearlPos, 12);

            PearlInfo info = new PearlInfo(ownerId, pearlPos, trapdoor, now);
            pearlMap.put(ownerId, info);
            LOGGER.debug("Tracked pearl for {} at {} (trapdoor {})", ownerPlayer.getGameProfile().getName(), pearlPos, trapdoor);
        }

        // Prune stale entries
        pearlMap.entrySet().removeIf(e -> now - e.getValue().createdAt > 60000);
    }

    private BlockPos findNearestTrapdoor(Level level, BlockPos center, int searchRadius) {
        if (level == null || center == null) return null;
        for (int r = 0; r <= searchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, 0, dz);
                    try {
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof TrapDoorBlock) return pos;
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    /**
     * Attempt to pull the tracked pearl for `target` using actor's actions.
     * If the wrong pearl is pulled, apply punishment.
     */
    public boolean attemptPullForPlayer(ServerPlayer actor, ServerPlayer target) {
        if (actor == null || target == null) return false;
        UUID targetId = target.getUUID();
        PearlInfo info = pearlMap.get(targetId);
        if (info == null) {
            LOGGER.info("No tracked pearl for player {}", target.getGameProfile().getName());
            return false;
        }

        BlockPos trapdoor = info.nearestTrapdoor;
        if (trapdoor == null) {
            LOGGER.info("No trapdoor near pearl for player {}", target.getGameProfile().getName());
            return false;
        }

        // Attempt to interact with the trapdoor (toggle)
        try {
            boolean interacted = BlockInteractionUtils.toggleTrapdoor(actor, trapdoor);
            if (!interacted) {
                LOGGER.warn("Could not toggle trapdoor at {}", trapdoor);
                return false;
            }

            // After interacting, check the nearest tracked pearl to trapdoor and who owns it
            PearlInfo nearest = findNearestPearlTo(trapdoor, 6);
            if (nearest == null) {
                LOGGER.warn("No pearl found near trapdoor after interaction");
                return false;
            }
            if (!nearest.ownerId.equals(targetId)) {
                LOGGER.warn("Wrong pearl pulled: expected {} but found {}; applying punishment", target.getName().getString(), nearest.ownerId);
                if (punishmentSystem != null) punishmentSystem.punish("StasisModule", 5);
                return false;
            }

            LOGGER.info("Successfully pulled correct pearl for {} via trapdoor {}", target.getGameProfile().getName(), trapdoor);
            return true;
        } catch (Exception e) {
            LOGGER.debug("attemptPullForPlayer error: {}", e.getMessage());
            return false;
        }
    }

    private PearlInfo findNearestPearlTo(BlockPos pos, int radius) {
        PearlInfo best = null;
        double bestDist = Double.MAX_VALUE;
        for (PearlInfo p : pearlMap.values()) {
            double dist = p.pos.distToCenterSqr(pos);
            if (dist < bestDist && dist <= radius * radius) {
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }
}

class PearlInfo {
    public final UUID ownerId;
    public final BlockPos pos;
    public final BlockPos nearestTrapdoor;
    public final long createdAt;

    public PearlInfo(UUID ownerId, BlockPos pos, BlockPos nearestTrapdoor, long createdAt) {
        this.ownerId = ownerId;
        this.pos = pos;
        this.nearestTrapdoor = nearestTrapdoor;
        this.createdAt = createdAt;
    }
}
