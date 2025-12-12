package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Water Bucket Module: Clutch prevention of fall damage, counter lava/crystal traps.
 * Strategy:
 * - Place water when falling from critical height
 * - Counter lava by placing water
 * - Use water to negate crystal trap damage
 */
public class WaterBucketModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-waterbucket");

    private boolean enabled = false;
    private boolean hasWaterBucket = false;
    private long lastWaterPlace = 0;
    private static final long WATER_COOLDOWN = 200; // 0.2 seconds
    private static final float CRITICAL_FALL_HEIGHT = 5.0f;

    public void init() {
        LOGGER.info("WaterBucketModule initialized");
    }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || !hasWaterBucket || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();

        // Counter lava
        if (signals.inLava && canPlaceWater(now)) {
            placeWaterToCounterLava(player);
            lastWaterPlace = now;
            return;
        }

        // Prevent fall damage
        if (signals.falling && signals.fallHeight > CRITICAL_FALL_HEIGHT && canPlaceWater(now)) {
            placeWaterForFallDamageNegation(player, signals.fallHeight);
            lastWaterPlace = now;
            return;
        }

        // Counter crystal traps
        if (signals.inCombat() && signals.playerHealth < 10.0f && canPlaceWater(now)) {
            placeWaterForCrystalProtection(player);
            lastWaterPlace = now;
        }
    }

    private void placeWaterToCounterLava(ServerPlayer player) {
        LOGGER.debug("Placing water to counter lava...");
        try {
            // Find nearest lava block within small radius and place water adjacent
            int radius = 4;
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-radius, -2, -radius),
                    player.blockPosition().offset(radius, 2, radius))) {
                if (player.level().getBlockState(p).getBlock() == net.minecraft.world.level.block.Blocks.LAVA) {
                    net.minecraft.core.BlockPos placePos = p.above();
                    com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, placePos);
                    return;
                }
            }
            // Fallback: place at player's feet
            com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, player.blockPosition());
        } catch (Exception e) {
            LOGGER.debug("Error placing water: {}", e.getMessage());
        }
    }

    private void placeWaterForFallDamageNegation(ServerPlayer player, float fallHeight) {
        LOGGER.debug("Placing water to negate fall damage (height: {})", fallHeight);
        try {
            int down = Math.max(1, (int) Math.ceil(fallHeight));
            net.minecraft.core.BlockPos target = player.blockPosition().below(down);
            com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, target);
        } catch (Exception e) {
            LOGGER.debug("Error placing water for fall: {}", e.getMessage());
        }
    }

    private void placeWaterForCrystalProtection(ServerPlayer player) {
        LOGGER.debug("Placing water for crystal protection...");
        try {
            // Place water in a 1-block ring around player to absorb explosions
            int radius = 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    net.minecraft.core.BlockPos pos = player.blockPosition().offset(dx, -1, dz);
                    com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, pos);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error placing water for crystal protection: {}", e.getMessage());
        }
    }

    private boolean canPlaceWater(long now) {
        return (now - lastWaterPlace) >= WATER_COOLDOWN && hasWaterBucket;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setHasWaterBucket(boolean has) { this.hasWaterBucket = has; }
    public boolean hasWaterBucket() { return hasWaterBucket; }
}
