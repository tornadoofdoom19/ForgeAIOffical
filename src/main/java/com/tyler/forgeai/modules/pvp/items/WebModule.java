package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.core.ContextScanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Web Module: Place webs defensively to trap opponents or break webs when stuck.
 * Strategy:
 * - Place webs around opponent when escaping
 * - Break webs when stuck to regain mobility
 * - Combine with knockback items for disengage
 */
public class WebModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-web");

    private boolean enabled = false;
    private int webCount = 0;
    private long lastWebPlace = 0;
    private static final long WEB_COOLDOWN = 500; // 0.5 seconds
    private com.tyler.forgeai.ai.RewardSystem rewardSystem;
    private com.tyler.forgeai.ai.TrainingManager trainingManager;
    private com.tyler.forgeai.ai.PunishmentSystem punishmentSystem;

    public void init() {
        LOGGER.info("WebModule initialized");
    }

    public void setRewardSystem(com.tyler.forgeai.ai.RewardSystem rs) { this.rewardSystem = rs; }
    public void setTrainingManager(com.tyler.forgeai.ai.TrainingManager tm) { this.trainingManager = tm; }
    public void setPunishmentSystem(com.tyler.forgeai.ai.PunishmentSystem ps) { this.punishmentSystem = ps; }

    public void tick(MinecraftServer server, ContextScanner.Signals signals) {
        if (!enabled || server == null) return;

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        if (player == null) return;

        long now = System.currentTimeMillis();
        
        // Check if we're stuck in web
        if (isPlayerStuckInWeb(player)) {
            breakWebsAround(player);
            return;
        }

        // Place webs defensively when low health and opponent nearby
        if (signals.inCombat() && signals.playerHealth < 8.0f && 
            !player.getInventory().isEmpty() && canPlaceWeb(now)) {
            // If single opponent nearby, attempt trap-for-crit sequence
            if (signals.nearbyOpponents <= 1) {
                placeWebAroundOpponentForCrit(player, signals);
            } else {
                placeWebAroundOpponent(player);
            }
            lastWebPlace = now;
        }
    }

    private boolean isPlayerStuckInWeb(ServerPlayer player) {
        // Check nearby block at player's feet for cobweb
        try {
            var pos = player.blockPosition();
            var state = player.level().getBlockState(pos);
            if (state.getBlock() == net.minecraft.world.level.block.Blocks.COBWEB) return true;
        } catch (Exception e) {
            // Fallback to movement speed when world query fails
            return player.getSpeed() < 0.5f;
        }
        return false;
    }

    private void breakWebsAround(ServerPlayer player) {
        LOGGER.debug("Breaking webs to escape...");
        try {
            int radius = 3;
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-radius, -1, -radius),
                    player.blockPosition().offset(radius, 1, radius))) {
                if (player.level().getBlockState(p).getBlock() == net.minecraft.world.level.block.Blocks.COBWEB) {
                    com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, p);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error breaking webs: {}", e.getMessage());
        }
    }

    private void placeWebAroundOpponent(ServerPlayer player) {
        LOGGER.debug("Placing webs defensively...");
        try {
            if (webCount <= 0) return;
            // Attempt to place a web in front of player to block pursuit
            net.minecraft.core.BlockPos target = player.blockPosition().relative(player.getDirection());
            com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, target);
            webCount--;
        } catch (Exception e) {
            LOGGER.debug("Error placing web: {}", e.getMessage());
        }
    }

    private void placeWebAroundOpponentForCrit(ServerPlayer player, ContextScanner.Signals signals) {
        // Strategy: place webs to trap a single opponent and allow critical hits
        LOGGER.info("Placing webs to trap opponent for crit sequence (hp={})", signals.playerHealth);
        try {
            if (webCount <= 0) return;
            // If signals provides opponent position, use it; otherwise place at player's front
            net.minecraft.core.BlockPos target = player.blockPosition().relative(player.getDirection());
            com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, target);
            webCount--;
            if (trainingManager != null) trainingManager.recordSuccess("WebModule.trap_attempt");
            if (rewardSystem != null) rewardSystem.reward("WebModule.trap", 3);
        } catch (Exception e) {
            LOGGER.debug("Error placing crit web: {}", e.getMessage());
        }
    }

    private boolean canPlaceWeb(long now) {
        return (now - lastWebPlace) >= WEB_COOLDOWN && webCount > 0;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setWebCount(int count) { this.webCount = count; }
    public int getWebCount() { return webCount; }
}
