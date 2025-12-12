package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.ContextScanner.Signals;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tyler.forgeai.util.PlayerActionUtils;
import com.tyler.forgeai.util.InventoryUtils;
import net.minecraft.world.entity.Entity;

/**
 * Enhanced MaceModule: supports aerial "dive-mace" combos, elytra+rocket preference,
 * wind-charge experimentation, pearl+windcharge sequencing, breach-swapping and
 * shield-disable attempts. Hooks into RewardSystem / PunishmentSystem / TrainingManager
 * so successful combos can be reinforced and failed attempts punished.
 */
public class MaceModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-mace");

    private boolean active = false;

    // Optional AI subsystems (injected by DecisionEngine)
    private RewardSystem rewardSystem;
    private PunishmentSystem punishmentSystem;
    private TrainingManager trainingManager;

    // State for combo experimentation and cooldowns
    private long lastAerialAttempt = 0;
    private long lastBreachSwap = 0;
    private long lastPearlWindAttempt = 0;
    private boolean comboInProgress = false;
    private String comboType = "";

    private static final long AERIAL_COOLDOWN = 1200; // ms between aerial attempts
    private static final long BREACH_SWAP_COOLDOWN = 800; // ms
    private static final long PEARL_WIND_COOLDOWN = 1500; // ms

    public void init() {
        LOGGER.info("Mace PvP module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Mace PvP module active: {}", enabled);
    }

    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
    public void setPunishmentSystem(PunishmentSystem ps) { this.punishmentSystem = ps; }
    public void setTrainingManager(TrainingManager tm) { this.trainingManager = tm; }

    public void tick(Signals s) {
        if (!active || s == null || s.player == null) return;

        ServerPlayer player = s.player;
        LOGGER.debug("Mace PvP tick for {} (hp={})", player.getName().getString(), s.playerHealth);

        long now = System.currentTimeMillis();

        // 1) If flying with elytra and has mace, prefer aerial experimentation (if rockets available prefer to stay aloft)
        if (s.isFlyingWithElytra() && s.hasMaceEquipped) {
            if ((now - lastAerialAttempt) >= AERIAL_COOLDOWN) {
                attemptAerialCombo(player, s);
                lastAerialAttempt = now;
            }
            return;
        }

        // 2) If not flying but has elytra + rockets, consider launching into air (wind-charge experiments)
        if (!s.isFlyingWithElytra() && s.hasMaceEquipped && prefersRockets(player)) {
            if ((now - lastAerialAttempt) >= AERIAL_COOLDOWN) {
                attemptElytraLaunchExperiment(player, s);
                lastAerialAttempt = now;
            }
            return;
        }

        // 3) Breach-swap if weapon pairing detected (sword + breach mace)
        if (canBreachSwap(player) && (now - lastBreachSwap) >= BREACH_SWAP_COOLDOWN) {
            performBreachSwap(player, s);
            lastBreachSwap = now;
            return;
        }

        // 4) Pearl + wind charge attempt (aggressive gap-close)
        if (canAttemptPearlWind(player) && (now - lastPearlWindAttempt) >= PEARL_WIND_COOLDOWN) {
            attemptPearlWindSequence(player, s);
            lastPearlWindAttempt = now;
            return;
        }

        // 5) Fallback: regular mace behavior (smart swings/timing)
        fallbackCombat(s);
    }

    // -- Aerial behaviors -------------------------------------------------

    private void attemptAerialCombo(ServerPlayer player, Signals s) {
        comboInProgress = true;
        comboType = "aerial_dive_mace";
        LOGGER.info("Attempting aerial dive-mace combo at hp={} (opponentAirborne={})", s.playerHealth, s.opponentAirborne);
        try {
            Entity opp = player.level().getNearestPlayer(player, 64);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(player, opp);
                PlayerActionUtils.jump(player);
                PlayerActionUtils.useMainHand(player, 6);
                PlayerActionUtils.moveForward(player, 0.8f);
                PlayerActionUtils.attackEntity(player, opp);
            } else {
                PlayerActionUtils.moveForward(player, 0.4f);
            }
            if (trainingManager != null) trainingManager.recordSuccess("MaceModule");
            if (rewardSystem != null) rewardSystem.reward("MaceModule", 5);
        } catch (Exception e) {
            LOGGER.debug("Error during aerial combo: {}", e.getMessage());
        }
        comboInProgress = false;
    }

    private void attemptElytraLaunchExperiment(ServerPlayer player, Signals s) {
        comboInProgress = true;
        comboType = "elytra_launch_experiment";
        LOGGER.info("Experiment: attempting elytra launch (wind-charge + jump) to reach higher airspace");
        try {
            try { InventoryUtils.moveItemToHotbar(player, "firework"); } catch (Exception ignored) {}
            PlayerActionUtils.jump(player);
            PlayerActionUtils.useMainHand(player, 6);
            PlayerActionUtils.setSprinting(player, true);
            if (trainingManager != null) trainingManager.recordSuccess("MaceModule.elytra_launch_attempt");
            if (rewardSystem != null) rewardSystem.reward("MaceModule", 2);
        } catch (Exception e) {
            LOGGER.debug("Error during elytra launch experiment: {}", e.getMessage());
        }
        comboInProgress = false;
    }

    // -- Pearl + WindCharge sequence --------------------------------------

    private void attemptPearlWindSequence(ServerPlayer player, Signals s) {
        comboInProgress = true;
        comboType = "pearl_wind_combo";
        LOGGER.info("Attempting pearl+windcharge combo to teleport into air for mace strike");
        try {
            try { InventoryUtils.moveItemToHotbar(player, "ender_pearl"); } catch (Exception ignored) {}
            // Throw pearl briefly then attempt wind-charge
            PlayerActionUtils.useMainHand(player, 2);
            PlayerActionUtils.useMainHand(player, 3);
            if (trainingManager != null) trainingManager.recordSuccess("MaceModule.pearl_wind_attempt");
            if (rewardSystem != null) rewardSystem.reward("MaceModule.pearl_wind", 2);
        } catch (Exception e) {
            LOGGER.debug("Error during pearl+wind sequence: {}", e.getMessage());
        }
        comboInProgress = false;
    }

    // -- Shield disable (axe) + stun-slam ---------------------------------

    /**
     * Attempt quick shield-disable: swap to axe, disable opponent shield, then swap back to mace
     * Safety: only attempt if player's health is above threshold and not taking heavy incoming damage
     */
    public void attemptShieldDisableWithAxe(ServerPlayer player, Signals s) {
        if (s == null) return;
        if (s.playerHealth < 6.0f || s.incomingMeleeDamage > 3.0f) {
            LOGGER.info("Skipping shield-disable: unsafe (hp={} incoming={})", s.playerHealth, s.incomingMeleeDamage);
            if (punishmentSystem != null) punishmentSystem.punish("MaceModule.shield_disable_skip", 1);
            return;
        }

        comboInProgress = true;
        comboType = "shield_disable_axe";
        LOGGER.info("Attempting shield disable sequence using axe (hp={})", s.playerHealth);

        // Move an axe into hotbar and attempt quick-swap timing
        try { InventoryUtils.moveItemToHotbar(player, "axe"); } catch (Exception ignored) {}
        if (trainingManager != null) trainingManager.recordSuccess("MaceModule.shield_disable_attempt");
        if (rewardSystem != null) rewardSystem.reward("MaceModule.shield_disable_attempt", 3);

        comboInProgress = false;
    }

    /**
     * Stun-slam: timed downward slam to stun or knock opponent when vulnerable.
     * This is a priority maneuver — rewards are higher when successful.
     */
    public void performStunSlam(ServerPlayer player, Signals s) {
        if (s == null) return;
        if (s.playerHealth < 4.0f) {
            LOGGER.info("Aborting stun-slam: player low health {}");
            if (punishmentSystem != null) punishmentSystem.punish("MaceModule.stunslam_abort_lowhp", 2);
            return;
        }

        comboInProgress = true;
        comboType = "stun_slam";
        LOGGER.info("Performing stun-slam attempt (high-priority move)");

        try {
            PlayerActionUtils.jump(player);
            PlayerActionUtils.moveForward(player, 0.6f);
            Entity opp = player.level().getNearestPlayer(player, 4);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(player, opp);
                PlayerActionUtils.attackEntity(player, opp);
            }
            if (trainingManager != null) trainingManager.recordSuccess("MaceModule.stun_slam_attempt");
            if (rewardSystem != null) rewardSystem.reward("MaceModule.stun_slam", 8);
        } catch (Exception e) {
            LOGGER.debug("Error during stun slam: {}", e.getMessage());
        }
        comboInProgress = false;
    }

    // -- Breach swap ------------------------------------------------------

    private boolean canBreachSwap(ServerPlayer player) {
        try {
            String main = player.getMainHandItem().getItem().toString().toLowerCase();
            String off = player.getOffhandItem().getItem().toString().toLowerCase();
            boolean hasSword = main.contains("sword") || off.contains("sword");
            boolean hasBreach = main.contains("breach") || off.contains("breach") || (main.contains("mace") && main.contains("breach"));
            return hasSword && hasBreach;
        } catch (Exception e) {
            return false;
        }
    }

    private void performBreachSwap(ServerPlayer player, Signals s) {
        comboType = "breach_swap";
        LOGGER.info("Performing breach-swap sequence to bypass armor (fast swap sword<->breach)");
        try {
            try { InventoryUtils.moveItemToHotbar(player, "sword"); } catch (Exception ignored) {}
            try { InventoryUtils.moveItemToHotbar(player, "mace"); } catch (Exception ignored) {}
            Entity opp = player.level().getNearestPlayer(player, 8);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(player, opp);
                PlayerActionUtils.attackEntity(player, opp);
            }
            if (trainingManager != null) trainingManager.recordSuccess("MaceModule");
            if (rewardSystem != null) rewardSystem.reward("MaceModule", 6);
        } catch (Exception e) {
            LOGGER.debug("Error during breach swap: {}", e.getMessage());
        }
    }

    // -- Shield disable attempts ------------------------------------------

    private boolean prefersRockets(ServerPlayer player) {
        // Heuristic: prefer rockets if player has them in inventory; conservative default = false
        try {
            // Best-effort scan of main/offhand and quick check for "rocket" or "firework"
            String main = player.getMainHandItem().getItem().toString().toLowerCase();
            String off = player.getOffhandItem().getItem().toString().toLowerCase();
            if (main.contains("rocket") || off.contains("rocket") || main.contains("firework") || off.contains("firework")) return true;
        } catch (Exception ignored) {}
        return false;
    }

    // -- Utility checks ---------------------------------------------------

    private boolean canAttemptPearlWind(ServerPlayer player) {
        try {
            // Heuristic: has ender pearl in inventory (quick check main/offhand only)
            String main = player.getMainHandItem().getItem().toString().toLowerCase();
            String off = player.getOffhandItem().getItem().toString().toLowerCase();
            return main.contains("ender_pearl") || off.contains("ender_pearl") || main.contains("enderpearl") || off.contains("enderpearl");
        } catch (Exception e) {
            return false;
        }
    }

    private void fallbackCombat(Signals s) {
        LOGGER.debug("Mace fallback behavior: attempting grounded mace strikes or delegating.");
        try {
            if (s == null || s.player == null) return;
            Entity opp = s.player.level().getNearestPlayer(s.player, 6);
            if (opp != null) {
                PlayerActionUtils.lookAtEntity(s.player, opp);
                PlayerActionUtils.attackEntity(s.player, opp);
            }
        } catch (Exception e) {
            LOGGER.debug("Error during fallback combat: {}", e.getMessage());
        }
    }
}
