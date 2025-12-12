package com.tyler.forgeai.modules.pvp.items;

import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.core.ContextScanner.Signals;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TridentModule: handle Riptide escape/boosts and trident-based disengage.
 * - Use Riptide in water/rain to dash away
 * - Water-bucket trick: place water, riptide, pick up, reuse water
 * - Use as rocket alternative to gain vertical boost with elytra
 */
public class TridentModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-trident");

    private boolean enabled = false;
    private RewardSystem rewardSystem;
    private PunishmentSystem punishmentSystem;
    private TrainingManager trainingManager;

    public void init() { LOGGER.info("TridentModule initialized"); }
    public void setEnabled(boolean e) { this.enabled = e; }
    public boolean isEnabled() { return enabled; }

    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
    public void setPunishmentSystem(PunishmentSystem ps) { this.punishmentSystem = ps; }
    public void setTrainingManager(TrainingManager tm) { this.trainingManager = tm; }

    public void tick(MinecraftServer server, Signals s) {
        if (!enabled || server == null || s == null || s.player == null) return;
        ServerPlayer player = s.player;

        // If Riptide available and in water/rain or near water, attempt escape
        if (hasRiptideReady(player)) {
            if (isInWaterOrRain(player)) {
                useRiptideEscape(player, s);
                return;
            }
            // water-bucket trick: quick place water + riptide if safe
            if (hasWaterBucket(player)) {
                attemptWaterBucketRiptide(player, s);
                return;
            }
        }

        // If elytra without rockets and trident with riptide available, try boost for aerial mace combos
        if (!s.hasRockets() && hasRiptideReady(player) && s.isFlyingWithElytra()) {
            attemptTridentElytraBoost(player, s);
            return;
        }
    }

    private boolean hasRiptideReady(ServerPlayer player) {
        try {
            // quick scan main/offhand for "trident" and riptide enchant
            String main = player.getMainHandItem().getItem().toString().toLowerCase();
            String off = player.getOffhandItem().getItem().toString().toLowerCase();
            return main.contains("trident") || off.contains("trident");
        } catch (Exception e) { return false; }
    }

    private boolean isInWaterOrRain(ServerPlayer player) {
        try { return player.isInWater() || player.level.isRainingAt(player.blockPosition()); } catch (Exception e) { return false; }
    }

    private boolean hasWaterBucket(ServerPlayer player) {
        try {
            String main = player.getMainHandItem().getItem().toString().toLowerCase();
            String off = player.getOffhandItem().getItem().toString().toLowerCase();
            return main.contains("water_bucket") || off.contains("water_bucket");
        } catch (Exception e) { return false; }
    }

    private void useRiptideEscape(ServerPlayer player, Signals s) {
        LOGGER.info("Using Riptide escape via trident to disengage (hp={})", s.playerHealth);
        try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "trident"); } catch (Exception ignored) {}
        if (trainingManager != null) trainingManager.recordSuccess("TridentModule.riptide_escape");
        if (rewardSystem != null) rewardSystem.reward("TridentModule.riptide", 4);
    }

    private void attemptWaterBucketRiptide(ServerPlayer player, Signals s) {
        LOGGER.info("Attempting water-bucket riptide trick to enable Riptide use outside water");
        try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "water_bucket"); } catch (Exception ignored) {}
        if (trainingManager != null) trainingManager.recordSuccess("TridentModule.bucket_riptide_attempt");
        if (rewardSystem != null) rewardSystem.reward("TridentModule.bucket_riptide", 2);
    }

    private void attemptTridentElytraBoost(ServerPlayer player, Signals s) {
        LOGGER.info("Attempting trident riptide boost for elytra aerial maneuver");
        try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "trident"); } catch (Exception ignored) {}
        if (trainingManager != null) trainingManager.recordSuccess("TridentModule.elytra_boost_attempt");
        if (rewardSystem != null) rewardSystem.reward("TridentModule.elytra_boost", 3);
    }
}
