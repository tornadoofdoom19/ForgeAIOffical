package com.tyler.forgeai.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates randomized PvP scenarios for testing.
 * - Aggressive opponent: rush, crystal burst, mace aerial
 * - Defensive opponent: shield block, totem clutch, web trap
 * - Mixed environment: falling, lava, ranged spam, armor break
 */
public class RandomizedPvPScenarioGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-scenario-gen");

    public enum ScenarioType {
        AGGRESSIVE_RUSH("Aggressive Rush", 0.25),
        AGGRESSIVE_CRYSTAL("Crystal Burst Opportunity", 0.25),
        AGGRESSIVE_MACE_AERIAL("Mace Aerial Attack", 0.2),
        DEFENSIVE_SHIELD("Enemy Shield Block", 0.2),
        DEFENSIVE_TOTEM("Totem Clutch Defense", 0.2),
        DEFENSIVE_WEB_TRAP("Web Trap", 0.15),
        MIXED_FALLING("Environmental - Falling", 0.15),
        MIXED_LAVA("Environmental - Lava", 0.15),
        MIXED_RANGED_SPAM("Ranged Spam Attack", 0.2),
        MIXED_ARMOR_BREAK("Armor Break Threat", 0.15);

        private final String displayName;
        private final double spawnWeight;

        ScenarioType(String displayName, double spawnWeight) {
            this.displayName = displayName;
            this.spawnWeight = spawnWeight;
        }

        public String getDisplayName() { return displayName; }
        public double getSpawnWeight() { return spawnWeight; }
    }

    private final Random random;

    public RandomizedPvPScenarioGenerator() {
        this.random = new Random();
    }

    public RandomizedPvPScenarioGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generate a random PvP scenario.
     */
    public PvPScenarioState generate() {
        ScenarioType type = selectRandomScenario();
        LOGGER.debug("Generating scenario: {}", type.getDisplayName());
        
        PvPScenarioState state = new PvPScenarioState(type);
        
        // Apply scenario-specific properties
        switch (type) {
            case AGGRESSIVE_RUSH:
                state.inCombat = true;
                state.opponentAggressive = true;
                state.incomingMeleeDamage = 5.0f + random.nextFloat() * 5.0f;
                state.opponentDistance = 2.0f + random.nextFloat() * 3.0f;
                state.recommended = "SwordModule or MaceModule for melee counter";
                break;

            case AGGRESSIVE_CRYSTAL:
                state.inCombat = true;
                state.crystalOpportunity = true;
                state.opponentAggressive = true;
                state.incomingMeleeDamage = 4.0f;
                state.recommended = "CrystalModule for burst damage";
                break;

            case AGGRESSIVE_MACE_AERIAL:
                state.inCombat = true;
                state.opponentAirborne = true;
                state.opponentAggressive = true;
                state.incomingMeleeDamage = 8.0f + random.nextFloat() * 4.0f; // Mace aerial is high damage
                state.recommendedInventory.add(RandomizedPvPInventory.PvPItem.ELYTRA);
                state.recommendedInventory.add(RandomizedPvPInventory.PvPItem.MACE);
                state.recommended = "MaceModule with elytra flight for counter-aerial or shield for defense";
                break;

            case DEFENSIVE_SHIELD:
                state.inCombat = true;
                state.opponentShieldActive = true;
                state.opponentDistance = 2.5f;
                state.incomingMeleeDamage = 0.5f; // Shield blocks most
                state.recommended = "SwordModule for sustained pressure or use pearls to flank";
                break;

            case DEFENSIVE_TOTEM:
                state.inCombat = true;
                state.opponentTotemAvailable = true;
                state.incomingDamageRisk = 0.7f;
                state.recommended = "Keep aggressive pressure or prepare pearl/wind charge escape";
                break;

            case DEFENSIVE_WEB_TRAP:
                state.inCombat = true;
                state.webTrapDetected = true;
                state.movementRestricted = true;
                state.incomingMeleeDamage = 3.0f + random.nextFloat() * 2.0f;
                state.recommendedInventory.add(RandomizedPvPInventory.PvPItem.WEBS);
                state.recommended = "Break web quickly, use wind charge or pearl to escape";
                break;

            case MIXED_FALLING:
                state.falling = true;
                state.fallHeight = 5.0f + random.nextFloat() * 15.0f;
                state.incomingMeleeDamage = 0;
                state.recommendedInventory.add(RandomizedPvPInventory.PvPItem.WATER_BUCKET);
                state.recommended = "Use water bucket to negate fall damage or build block";
                break;

            case MIXED_LAVA:
                state.inLava = true;
                state.incomingEnvironmentalDamage = 2.0f; // Lava tick damage
                state.movementRestricted = true;
                state.recommended = "Use water bucket or get out immediately";
                break;

            case MIXED_RANGED_SPAM:
                state.inCombat = true;
                state.opponentAggressive = true;
                state.incomingProjectileDamage = 2.0f + random.nextFloat() * 3.0f; // Arrow/projectile
                state.opponentDistance = 10.0f + random.nextFloat() * 20.0f;
                state.recommended = "Shield for defense or use pearls/elytra for repositioning";
                break;

            case MIXED_ARMOR_BREAK:
                state.inCombat = true;
                state.armorBreakThreat = true;
                state.opponentAggressive = true;
                state.incomingMeleeDamage = 4.0f + random.nextFloat() * 4.0f;
                state.recommended = "Disengage and restock/repair armor if available";
                break;
        }

        // Add randomization to all scenarios
        if (random.nextDouble() < 0.4) {
            state.opponentHealth = 5.0f + random.nextFloat() * 15.0f;
        }

        return state;
    }

    /**
     * Select a random scenario type based on weights.
     */
    private ScenarioType selectRandomScenario() {
        double totalWeight = 0;
        for (ScenarioType type : ScenarioType.values()) {
            totalWeight += type.getSpawnWeight();
        }

        double pick = random.nextDouble() * totalWeight;
        double accumulated = 0;

        for (ScenarioType type : ScenarioType.values()) {
            accumulated += type.getSpawnWeight();
            if (pick <= accumulated) {
                return type;
            }
        }

        return ScenarioType.AGGRESSIVE_RUSH; // Fallback
    }

    // ---- Scenario State ----

    public static class PvPScenarioState {
        public final ScenarioType scenarioType;
        
        // Combat state
        public boolean inCombat = false;
        public boolean opponentAggressive = false;
        public boolean opponentShieldActive = false;
        public boolean opponentTotemAvailable = false;
        public boolean opponentAirborne = false;
        
        // Damage threats
        public float incomingMeleeDamage = 0;
        public float incomingProjectileDamage = 0;
        public float incomingEnvironmentalDamage = 0;
        public float incomingDamageRisk = 0; // 0.0-1.0 probability of imminent lethal damage
        
        // Environment
        public boolean falling = false;
        public float fallHeight = 0;
        public boolean inLava = false;
        public boolean webTrapDetected = false;
        public boolean movementRestricted = false;
        public boolean crystalOpportunity = false;
        public boolean armorBreakThreat = false;
        
        // Positioning
        public float opponentDistance = 0;
        public float opponentHealth = 20.0f;
        
        // Recommendations
        public final List<RandomizedPvPInventory.PvPItem> recommendedInventory = new ArrayList<>();
        public String recommended = "";

        public PvPScenarioState(ScenarioType scenarioType) {
            this.scenarioType = scenarioType;
        }

        public String summarize() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Scenario: ").append(scenarioType.getDisplayName()).append(" ===\n");
            
            if (inCombat) {
                sb.append("Combat: YES").append("\n");
                if (opponentAggressive) sb.append("Opponent: AGGRESSIVE").append("\n");
                if (opponentShieldActive) sb.append("Opponent: Shield Active").append("\n");
                if (opponentTotemAvailable) sb.append("Opponent: Totem Available").append("\n");
                if (opponentAirborne) sb.append("Opponent: Airborne").append("\n");
                sb.append(String.format("Opponent Health: %.1f/20\n", opponentHealth));
                sb.append(String.format("Opponent Distance: %.1f blocks\n", opponentDistance));
            }
            
            if (incomingMeleeDamage > 0) sb.append(String.format("Incoming Melee Damage: %.1f\n", incomingMeleeDamage));
            if (incomingProjectileDamage > 0) sb.append(String.format("Incoming Projectile Damage: %.1f\n", incomingProjectileDamage));
            if (incomingEnvironmentalDamage > 0) sb.append(String.format("Environmental Damage/Tick: %.1f\n", incomingEnvironmentalDamage));
            if (incomingDamageRisk > 0) sb.append(String.format("Lethal Damage Risk: %.0f%%\n", incomingDamageRisk * 100));
            
            if (falling) sb.append(String.format("Falling: %.1f blocks\n", fallHeight));
            if (inLava) sb.append("In Lava: YES\n");
            if (webTrapDetected) sb.append("Web Trap: DETECTED\n");
            if (movementRestricted) sb.append("Movement: RESTRICTED\n");
            if (crystalOpportunity) sb.append("Crystal Opportunity: YES\n");
            if (armorBreakThreat) sb.append("Armor Break Threat: YES\n");
            
            if (!recommendedInventory.isEmpty()) {
                sb.append("Recommended Items: ");
                for (RandomizedPvPInventory.PvPItem item : recommendedInventory) {
                    sb.append(item.getDisplayName()).append(", ");
                }
                sb.setLength(sb.length() - 2);
                sb.append("\n");
            }
            
            sb.append("Recommended Action: ").append(recommended).append("\n");
            
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("PvPScenarioState{%s, combat=%s, incoming=%.1f+%.1f}",
                    scenarioType.getDisplayName(), inCombat,
                    incomingMeleeDamage, incomingProjectileDamage);
        }
    }
}
