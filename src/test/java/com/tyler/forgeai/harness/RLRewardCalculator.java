package com.tyler.forgeai.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Reinforcement Learning reward calculator for PvP scenarios.
 * Rewards:
 * - Survival (health preserved, totems used correctly)
 * - Effective item usage (shield blocks, correct timing)
 * - Tactical decisions (correct module selection)
 * 
 * Punishments:
 * - Death
 * - Reckless overcommitment
 * - Wasted consumables (totems, potions)
 * - Wrong timing (shield held when lethal damage bypasses)
 */
public class RLRewardCalculator {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-rl");

    // Reward tiers
    private static final int REWARD_SURVIVAL_BONUS = 100;
    private static final int REWARD_VICTORY = 500;
    private static final int REWARD_SHIELD_BLOCKED = 50;
    private static final int REWARD_TOTEM_USED = 75;
    private static final int REWARD_EFFECTIVE_POTION = 40;
    private static final int REWARD_SUCCESSFUL_DISENGAGE = 60;
    private static final int REWARD_CORRECT_MODULE = 30;

    private static final int PUNISHMENT_DEATH = -300;
    private static final int PUNISHMENT_RECKLESS = -150;
    private static final int PUNISHMENT_WASTED_TOTEM = -100;
    private static final int PUNISHMENT_WRONG_TIMING = -80;
    private static final int PUNISHMENT_WASTED_POTION = -50;
    private static final int PUNISHMENT_WRONG_MODULE = -40;

    // Session tracking
    private int totalRewards = 0;
    private int totalPunishments = 0;
    private final List<RLEvent> events = new ArrayList<>();

    public void reset() {
        totalRewards = 0;
        totalPunishments = 0;
        events.clear();
    }

    // ---- Public Reward Methods ----

    /**
     * Reward survival: Player health preserved and threats mitigated.
     */
    public void rewardSurvival(float healthPreserved, float damageBlocked) {
        int reward = REWARD_SURVIVAL_BONUS;
        if (healthPreserved > 15.0f) reward += 50; // Excellent health
        if (damageBlocked > 5.0f) reward += Math.min(100, (int) (damageBlocked * 10));
        
        applyReward(reward, "SURVIVAL", 
                "Health preserved: " + healthPreserved + ", Damage blocked: " + damageBlocked);
    }

    /**
     * Reward victory: Opponent defeated.
     */
    public void rewardVictory(float opponentHealth, int roundsWon) {
        int reward = REWARD_VICTORY + (roundsWon * 50);
        applyReward(reward, "VICTORY", "Opponent defeated with " + roundsWon + " successful exchanges");
    }

    /**
     * Reward shield usage: Successful damage block.
     */
    public void rewardShieldBlock(float damageBlocked, boolean timingCorrect) {
        int reward = REWARD_SHIELD_BLOCKED;
        if (timingCorrect) reward += 30; // Bonus for correct timing
        reward += Math.min(50, (int) (damageBlocked * 5));
        
        applyReward(reward, "SHIELD_BLOCK", 
                "Blocked " + damageBlocked + " damage" + (timingCorrect ? " with correct timing" : ""));
    }

    /**
     * Reward totem usage: Death prevented.
     */
    public void rewardTotemUsage(float healthSaved, boolean timingCorrect) {
        int reward = REWARD_TOTEM_USED;
        if (timingCorrect) reward += 50; // Major bonus for correct timing
        reward += Math.min(100, (int) (healthSaved * 5));
        
        applyReward(reward, "TOTEM_USED", 
                "Prevented death, saved " + healthSaved + " health" + (timingCorrect ? " with correct timing" : ""));
    }

    /**
     * Reward effective potion usage.
     */
    public void rewardPotionUsage(String potionType, boolean beneficial) {
        int reward = beneficial ? REWARD_EFFECTIVE_POTION : 0;
        if (beneficial) reward += 20; // Bonus for choosing right potion
        
        applyReward(reward, "POTION_USED", potionType + " used " + (beneficial ? "beneficially" : ""));
    }

    /**
     * Reward successful disengagement: Escaped without death.
     */
    public void rewardSuccessfulDisengage(float distanceEscaped, boolean itemsPreserved) {
        int reward = REWARD_SUCCESSFUL_DISENGAGE;
        reward += Math.min(50, (int) (distanceEscaped / 2)); // Bonus for escaping far
        if (itemsPreserved) reward += 30;
        
        applyReward(reward, "DISENGAGE_SUCCESS", 
                "Escaped " + distanceEscaped + " blocks" + (itemsPreserved ? " with resources preserved" : ""));
    }

    /**
     * Reward correct module selection.
     */
    public void rewardCorrectModule(String moduleName, boolean conditionsOptimal) {
        int reward = REWARD_CORRECT_MODULE;
        if (conditionsOptimal) reward += 20;
        
        applyReward(reward, "CORRECT_MODULE", 
                moduleName + " selected" + (conditionsOptimal ? " in optimal conditions" : ""));
    }

    // ---- Public Punishment Methods ----

    /**
     * Punish death.
     */
    public void punishDeath(String cause) {
        applyPunishment(PUNISHMENT_DEATH, "DEATH", "Player died: " + cause);
    }

    /**
     * Punish reckless overcommitment.
     */
    public void punishRecklessness(float healthRemaining, boolean noEscapeItem) {
        int punishment = PUNISHMENT_RECKLESS;
        if (healthRemaining < 3.0f) punishment -= 50;
        if (noEscapeItem) punishment -= 50;
        
        applyPunishment(punishment, "RECKLESS", 
                "Overcommitted with " + healthRemaining + " health remaining" + (noEscapeItem ? " and no escape items" : ""));
    }

    /**
     * Punish wasted totem.
     */
    public void punishWastedTotem(float damageBlocked) {
        int punishment = PUNISHMENT_WASTED_TOTEM;
        if (damageBlocked < 2.0f) punishment -= 50; // Severe penalty for using on minor damage
        
        applyPunishment(punishment, "WASTED_TOTEM", 
                "Totem used unnecessarily, only blocked " + damageBlocked + " damage");
    }

    /**
     * Punish wrong timing: shield held when lethal damage bypasses.
     */
    public void punishWrongTiming(String situation, boolean lethal) {
        int punishment = PUNISHMENT_WRONG_TIMING;
        if (lethal) punishment -= 100; // Severe penalty if it causes death
        
        applyPunishment(punishment, "WRONG_TIMING", 
                "Poor timing in " + situation + (lethal ? " - resulted in death" : ""));
    }

    /**
     * Punish wasted potion.
     */
    public void punishWastedPotion(String potionType) {
        applyPunishment(PUNISHMENT_WASTED_POTION, "WASTED_POTION", 
                potionType + " used at suboptimal time");
    }

    /**
     * Punish wrong module selection.
     */
    public void punishWrongModule(String selectedModule, String recommendedModule) {
        int punishment = PUNISHMENT_WRONG_MODULE;
        applyPunishment(punishment, "WRONG_MODULE", 
                "Selected " + selectedModule + " instead of " + recommendedModule);
    }

    // ---- Internal Helpers ----

    private void applyReward(int points, String eventType, String description) {
        totalRewards += points;
        events.add(new RLEvent(eventType, points, description, System.currentTimeMillis()));
        LOGGER.debug("✓ REWARD: {} points - {} (Total: {})", points, eventType, totalRewards);
    }

    private void applyPunishment(int points, String eventType, String description) {
        totalPunishments += Math.abs(points);
        events.add(new RLEvent(eventType, points, description, System.currentTimeMillis()));
        LOGGER.debug("✗ PUNISHMENT: {} points - {} (Total: {})", points, eventType, totalPunishments);
    }

    // ---- Getters ----

    public int getTotalRewards() { return totalRewards; }
    public int getTotalPunishments() { return totalPunishments; }
    public int getNetRewards() { return totalRewards - totalPunishments; }
    public List<RLEvent> getEvents() { return new ArrayList<>(events); }

    /**
     * Calculate success score (0.0 - 1.0) based on rewards/punishments.
     */
    public float getSuccessScore() {
        int total = Math.max(1, totalRewards + totalPunishments);
        return (float) totalRewards / total;
    }

    // ---- Event Logging ----

    public String summarizeSession() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Reinforcement Learning Session Summary ===\n");
        sb.append(String.format("Total Rewards: %d points\n", totalRewards));
        sb.append(String.format("Total Punishments: %d points\n", totalPunishments));
        sb.append(String.format("Net: %d points\n", getNetRewards()));
        sb.append(String.format("Success Score: %.2f%%\n", getSuccessScore() * 100));
        sb.append(String.format("Events: %d\n", events.size()));
        
        if (!events.isEmpty()) {
            sb.append("\nTop Events:\n");
            int count = 0;
            for (RLEvent event : events) {
                if (count++ >= 10) break;
                sb.append("  ").append(event).append("\n");
            }
        }
        
        return sb.toString();
    }

    // ---- RL Event Record ----

    public static class RLEvent {
        public final String eventType;
        public final int points;
        public final String description;
        public final long timestamp;

        public RLEvent(String eventType, int points, String description, long timestamp) {
            this.eventType = eventType;
            this.points = points;
            this.description = description;
            this.timestamp = timestamp;
        }

        public boolean isReward() { return points > 0; }
        public boolean isPunishment() { return points < 0; }

        @Override
        public String toString() {
            String prefix = isReward() ? "✓" : "✗";
            return String.format("%s [%s] %+d: %s", prefix, eventType, points, description);
        }
    }
}
