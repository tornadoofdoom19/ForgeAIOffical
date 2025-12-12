package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tyler.forgeai.util.PlayerActionUtils;
import com.tyler.forgeai.util.InventoryUtils;
import net.minecraft.world.entity.Entity;

public class SwordModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-sword");

    private boolean active = false;
    private com.tyler.forgeai.ai.RewardSystem rewardSystem;
    private com.tyler.forgeai.ai.PunishmentSystem punishmentSystem;
    private com.tyler.forgeai.ai.TrainingManager trainingManager;

    public void init() {
        LOGGER.info("Sword PvP module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Sword PvP module active: " + enabled);
    }

    public void setRewardSystem(com.tyler.forgeai.ai.RewardSystem rs) { this.rewardSystem = rs; }
    public void setPunishmentSystem(com.tyler.forgeai.ai.PunishmentSystem ps) { this.punishmentSystem = ps; }
    public void setTrainingManager(com.tyler.forgeai.ai.TrainingManager tm) { this.trainingManager = tm; }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Sword PvP tick running for player: " + s.player.getName().getString());

        if (s.inCombat()) {
            engageOpponent(s);
        } else {
            LOGGER.debug("No combat detected — SwordModule idle.");
        }
    }

    private void engageOpponent(Signals s) {
        LOGGER.info("Engaging opponent with sword combat.");
        try {
            Entity opp = s.player.level().getNearestPlayer(s.player, 8);
            if (opp == null) {
                // Move toward approximate opponent direction if none in immediate range
                PlayerActionUtils.moveForward(s.player, 0.4f);
                return;
            }

            // Approach and strafe
            PlayerActionUtils.lookAtEntity(s.player, opp);
            PlayerActionUtils.moveInDirection(s.player, 0.6f, 0.2f);

            // If low health, retreat briefly
            if (s.playerHealth < 6.0f) {
                PlayerActionUtils.moveInDirection(s.player, -0.6f, 0.0f);
                return;
            }

            // Perform critical attempt: jump and attack during fall
            PlayerActionUtils.jump(s.player);
            PlayerActionUtils.attackEntity(s.player, opp);

            // Follow-up swings
            PlayerActionUtils.swingMainHand(s.player);
            PlayerActionUtils.attackEntity(s.player, opp);

            // Reward small aggressions
            if (trainingManager != null) trainingManager.recordSuccess("SwordModule.engage");
            if (rewardSystem != null) rewardSystem.reward("SwordModule.engage", 1);
        } catch (Exception e) {
            LOGGER.debug("Error during sword engage: {}", e.getMessage());
        }
        
        // If opponent has shield up and we have an axe, attempt to disable shield safely
        if (s.opponentHasShield && hasAxeAvailable(s)) {
            attemptShieldDisable(s);
            return;
        }

        // Otherwise regular sword combos handled above
    }

    /**
     * Experiment with W-tap timing to find the best sprint-toggle rhythm for combo retention.
     */
    public void experimentWTap(Signals s) {
        LOGGER.info("Experimenting W-tap timing to find optimal combo rhythm");
        if (trainingManager != null) trainingManager.recordSuccess("SwordModule.wtap_attempt");
        if (rewardSystem != null) rewardSystem.reward("SwordModule.wtap", 1);
    }

    private boolean hasAxeAvailable(Signals s) {
        try {
            String main = s.player.getMainHandItem().getItem().toString().toLowerCase();
            String off = s.player.getOffhandItem().getItem().toString().toLowerCase();
            return main.contains("axe") || off.contains("axe");
        } catch (Exception e) {
            return false;
        }
    }

    private void attemptShieldDisable(Signals s) {
        if (s.playerHealth < 6.0f || s.incomingMeleeDamage > 3.0f) {
            LOGGER.info("SwordModule: unsafe to attempt shield-disable (hp={}, incoming={})", s.playerHealth, s.incomingMeleeDamage);
            if (punishmentSystem != null) punishmentSystem.punish("SwordModule.shield_disable_skip", 1);
            return;
        }

        LOGGER.info("SwordModule: attempting shield-disable with axe then resume sword pressure");
        try {
            // Move axe and sword to hotbar and select axe, hit, then return to sword
            InventoryUtils.moveItemToHotbar(s.player, "axe");
            InventoryUtils.moveItemToHotbar(s.player, "sword");
            // Assume hotbar selection done by InventoryUtils; perform quick attack
            PlayerActionUtils.useMainHand(s.player, 2);
            Entity opp = s.player.level().getNearestPlayer(s.player, 4);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(s.player, opp);
                PlayerActionUtils.attackEntity(s.player, opp);
            }
            // Swap back to sword (InventoryUtils will prefer sword if present)
            InventoryUtils.moveItemToHotbar(s.player, "sword");
            if (trainingManager != null) trainingManager.recordSuccess("SwordModule.shield_disable_attempt");
            if (rewardSystem != null) rewardSystem.reward("SwordModule.shield_disable", 4);
        } catch (Exception e) {
            LOGGER.debug("Error during shield-disable sequence: {}", e.getMessage());
        }
    }
}
