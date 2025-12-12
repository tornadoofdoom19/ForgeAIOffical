# Integration Guide: Randomized PvP Test Harness & DecisionEngine

## Overview

This guide explains how to integrate the Randomized PvP Test Harness with your existing DecisionEngine to enable AI evaluation and reinforcement learning.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  RandomizedPvPTestHarness (Orchestrator)                    │
│  - Manages test runs                                         │
│  - Coordinates all components                                │
└────────────┬──────────────────────────────────────────────┬─┘
             │                                              │
    ┌────────▼──────────┐                       ┌───────────▼─┐
    │ Randomization     │                       │ Evaluation  │
    │ ────────────────  │                       │ ──────────  │
    │ RandomizedPvP     │                       │ RLReward    │
    │ Inventory         │                       │ Calculator  │
    │                   │                       │             │
    │ RandomizedPvP     │                       │ CommandSignal│
    │ ScenarioGenerator │                       │ Analysis    │
    └────────┬──────────┘                       └──────┬──────┘
             │                                        │
             └────────────┬─────────────────────────┬─┘
                          │                         │
                    ┌─────▼─────┐            ┌─────▼─────┐
                    │ Context   │            │ Signals   │
                    │ Scanner   │            │ Queue     │
                    │ (Extended)│            │           │
                    └─────┬─────┘            └─────┬─────┘
                          │                        │
                          └──────────┬─────────────┘
                                     │
                          ┌──────────▼──────────┐
                          │  DecisionEngine     │
                          │  - Evaluates signals│
                          │  - Selects module   │
                          │  - Provides feedback│
                          └─────────────────────┘
```

## Component Integration

### 1. ContextScanner Integration

The test harness injects synthetic signals into ContextScanner:

```java
// Test harness creates synthetic signals from inventory & scenario
ContextScanner.Signals signals = buildSignalsFromState(inventory, scenario);

// Injects into ContextScanner (used by DecisionEngine)
mockScanner.setLastSignals(signals);

// DecisionEngine calls tick(null) which uses the synthetic signals
engine.tick(null);
```

**Key Enhancement**: `ContextScanner.Signals` now includes:
- Player health, armor durability
- Incoming damage threats
- Opponent state
- Environmental hazards
- Active potion effects

### 2. DecisionEngine Integration

The DecisionEngine receives signals and makes decisions:

```java
// In your DecisionEngine.tick() method:
public void tick(MinecraftServer server) {
    ContextScanner.Signals s = scanner.sample(server);
    // scanner.sample(null) returns synthetic signals during testing
    
    if (s.inCombat()) {
        // Combat module selection
        if (s.hasMaceEquipped && s.isFlyingWithElytra) {
            selectModule(maceModule); // Aerial attack opportunity
        } else if (s.isLowHealth && s.playerHealth < 6.0f) {
            selectModule(shield); // Defensive play needed
        }
        // ... more logic
    }
}
```

### 3. Signal Processing Flow

```java
// 1. Test harness generates signals from state
List<CommandSignal> signals = CommandSignal.generateSignalsFromState(inventory, scenario);
CommandSignal.SignalQueue queue = new CommandSignal.SignalQueue();
queue.enqueueAll(signals);

// 2. Signals can be processed by your AI logic
while (!queue.isEmpty()) {
    CommandSignal signal = queue.dequeue(); // Highest priority first
    
    if (signal.isDirect()) {
        // Handle direct player command
        handleDirectCommand(signal);
    } else if (signal.isIndirect()) {
        // Handle implicit game signal
        handleIndirectSignal(signal);
    }
}

// 3. DecisionEngine makes final module selection
engine.tick(null);
String selectedModule = engine.getCurrentModule();
```

### 4. Reinforcement Learning Loop

```java
// Evaluate AI decision quality
RLRewardCalculator rl = new RLRewardCalculator();

// Check if module selection matches scenario recommendation
if (isCorrectModule(selectedModule, scenario)) {
    rl.rewardCorrectModule(selectedModule, true);
} else {
    rl.punishWrongModule(selectedModule, "Recommended");
}

// Evaluate survival decisions
if (scenario.inCombat) {
    if (inventory.getPlayerHealth() > 10.0f) {
        rl.rewardSurvival(inventory.getPlayerHealth(), incomingDamage);
    } else {
        rl.punishRecklessness(inventory.getPlayerHealth(), noEscapeItems);
    }
}

// Evaluate shield/totem timing
if (selectedModule.contains("Shield")) {
    rl.rewardShieldBlock(damageBlocked, correctTiming);
}
if (selectedModule.contains("Totem")) {
    rl.rewardTotemUsage(healthSaved, correctTiming);
}

// Final score
float score = rl.getSuccessScore(); // 0.0 to 1.0
```

## Full Integration Example

```java
public class DecisionEngine {
    private final ContextScanner scanner;
    private final CommunicationManager comms;
    
    // AI subsystems
    private TrainingManager trainingManager;
    private MemoryManager memoryManager;
    private RewardSystem rewardSystem;
    private PunishmentSystem punishmentSystem;
    
    // Modules (existing)
    private final MaceModule maceModule = new MaceModule();
    private final SwordModule swordModule = new SwordModule();
    private final ShieldModule shieldModule = new ShieldModule(); // New
    private final TotemModule totemModule = new TotemModule();   // New
    // ... other modules
    
    public DecisionEngine(ContextScanner scanner, CommunicationManager comms) {
        this.scanner = scanner;
        this.comms = comms;
    }
    
    /**
     * Main decision loop - called every game tick
     * Works with both real Minecraft server and synthetic test signals
     */
    public void tick(MinecraftServer server) {
        // Sample context (real or synthetic)
        ContextScanner.Signals s = scanner.sample(server);
        if (s == null) {
            ensureOnlyStasisActive();
            stasisModule.tick(s);
            return;
        }
        
        // ============ TEST HARNESS USES THIS PATH ============
        // s contains all combat state from RandomizedPvPInventory + Scenario
        
        // Evaluate threats and opportunities
        boolean lowHealth = s.playerHealth < 6.0f;
        boolean shieldEffective = s.opponentHasShield && !s.crystalOpportunity;
        boolean armorBroken = s.isArmorBroken;
        boolean fallingDanger = s.falling && s.fallHeight > 5.0f;
        boolean webTrapped = s.webTrapDetected;
        boolean opponentAirborne = s.opponentAirborne;
        
        // ============ DECISION LOGIC ============
        
        // SURVIVAL PRIORITY: Check immediate threats first
        if (lowHealth && s.incomingDamageRisk > 0.7f) {
            // Lethal damage imminent
            selectModule(totemModule);
            rewardSystem.reward("TotemModule", 75);
            return;
        }
        
        if (lowHealth && s.opponentHasShield) {
            // Low health with shielded opponent - defensive play
            selectModule(shieldModule);
            rewardSystem.reward("ShieldModule", 50);
            return;
        }
        
        // ENVIRONMENTAL THREATS
        if (fallingDanger) {
            selectModule(builderModule); // Water bucket / block placement
            rewardSystem.reward("BuilderModule", 60);
            return;
        }
        
        if (s.inLava) {
            selectModule(builderModule); // Water bucket to escape
            return;
        }
        
        if (webTrapped) {
            selectModule(builderModule); // Break web
            return;
        }
        
        // COMBAT DECISIONS
        if (s.inCombat()) {
            if (opponentAirborne && s.playerHealth > 10.0f) {
                // High risk aerial attack - use mace for counter
                selectModule(maceModule);
                rewardSystem.reward("MaceModule", 40);
                return;
            }
            
            if (s.crystalOpportunity) {
                // Crystal burst window available
                selectModule(crystalModule);
                rewardSystem.reward("CrystalModule", 100);
                return;
            }
            
            if (shieldEffective) {
                // Opponent shielding - sustained sword pressure
                selectModule(swordModule);
                rewardSystem.reward("SwordModule", 30);
                return;
            }
            
            // Default combat - sword/mace based on health
            if (s.playerHealth > 12.0f) {
                selectModule(maceModule);
            } else {
                selectModule(swordModule);
            }
            return;
        }
        
        // RESOURCE CHECKS
        if (armorBroken || !s.playerHealth > 8.0f) {
            // Critical resources depleted - disengage to restock
            selectModule(stasisModule); // Retreat and regroup
            return;
        }
        
        // NO THREATS - RESTORE PASSIVE MODE
        ensureOnlyStasisActive();
        stasisModule.tick(s);
    }
    
    private String currentModule = "StasisModule";
    
    private void selectModule(Object module) {
        if (module instanceof MaceModule) {
            currentModule = "MaceModule";
            maceModule.tick(null);
        } else if (module instanceof SwordModule) {
            currentModule = "SwordModule";
            swordModule.tick(null);
        } else if (module instanceof ShieldModule) {
            currentModule = "ShieldModule";
            shieldModule.tick(null);
        } else if (module instanceof TotemModule) {
            currentModule = "TotemModule";
            totemModule.tick(null);
        }
        // ... handle other modules
    }
    
    public String getCurrentModule() {
        return currentModule;
    }
    
    // Setters for AI subsystems (used by test harness)
    public void setTrainingManager(TrainingManager tm) { this.trainingManager = tm; }
    public void setMemoryManager(MemoryManager mm) { this.memoryManager = mm; }
    public void setRewardSystem(RewardSystem rs) { this.rewardSystem = rs; }
    public void setPunishmentSystem(PunishmentSystem ps) { this.punishmentSystem = ps; }
}
```

## Test Harness Evaluation Logic

The test harness evaluates DecisionEngine decisions:

```java
private void evaluatePerformance(
        String selectedModule,
        RandomizedPvPScenarioGenerator.PvPScenarioState scenario,
        RandomizedPvPInventory inventory,
        RLRewardCalculator rlCalc,
        List<CommandSignal> signals) {

    // 1. MODULE CORRECTNESS
    boolean correctModule = evaluateModuleChoice(selectedModule, scenario, inventory);
    if (correctModule) {
        rlCalc.rewardCorrectModule(selectedModule, true);
    } else {
        rlCalc.punishWrongModule(selectedModule, "Scenario recommendation");
    }
    
    // 2. SURVIVAL EVALUATION
    if (scenario.inCombat) {
        if (inventory.getPlayerHealth() > 10.0f) {
            rlCalc.rewardSurvival(inventory.getPlayerHealth(), scenario.incomingMeleeDamage);
        } else {
            rlCalc.punishRecklessness(inventory.getPlayerHealth(), 
                    !inventory.hasItem(RandomizedPvPInventory.PvPItem.ENDER_PEARL));
        }
    }
    
    // 3. SHIELD USAGE
    if (selectedModule.contains("Shield") && scenario.opponentShieldActive) {
        rlCalc.rewardShieldBlock(scenario.incomingMeleeDamage * 0.5f, true);
    }
    
    // 4. TOTEM USAGE
    if (selectedModule.contains("Totem") && inventory.getPlayerHealth() < 5.0f) {
        rlCalc.rewardTotemUsage(15.0f, true);
    } else if (selectedModule.contains("Totem") && inventory.getPlayerHealth() > 15.0f) {
        rlCalc.punishWastedTotem(0);
    }
}

// Scenario-specific module evaluation
private boolean evaluateModuleChoice(
        String module,
        RandomizedPvPScenarioGenerator.PvPScenarioState scenario,
        RandomizedPvPInventory inventory) {

    switch (scenario.scenarioType) {
        case AGGRESSIVE_RUSH:
            // Melee counter needed
            return module.contains("Sword") || module.contains("Mace");
        
        case AGGRESSIVE_CRYSTAL:
            // Crystal burst priority
            return module.contains("Crystal");
        
        case AGGRESSIVE_MACE_AERIAL:
            // Aerial combat
            return module.contains("Mace") || module.contains("Sword");
        
        case DEFENSIVE_SHIELD:
            // Pressure shielded opponent
            return module.contains("Sword") || module.contains("Shield");
        
        case DEFENSIVE_WEB_TRAP:
            // Break web, escape
            return module.contains("Sword") || module.contains("Builder");
        
        case MIXED_FALLING:
            // Water bucket / block
            return module.contains("Builder") || module.contains("Stasis");
        
        case MIXED_LAVA:
            // Water bucket
            return module.contains("Builder") || module.contains("Stasis");
        
        case MIXED_ARMOR_BREAK:
            // Disengage
            return module.contains("Stasis");
        
        default:
            return false;
    }
}
```

## Running Tests

### Test Single Run

```java
RandomizedPvPInventory inventory = new RandomizedPvPInventory(12345);
inventory.randomize();

RandomizedPvPScenarioGenerator gen = new RandomizedPvPScenarioGenerator(12345);
RandomizedPvPScenarioGenerator.PvPScenarioState scenario = gen.generate();

// Create fresh engine
DecisionEngine engine = new DecisionEngine(scanner, comms);
engine.init();

// Inject signals
ContextScanner.Signals signals = buildSignalsFromState(inventory, scenario);
scanner.setLastSignals(signals);

// Evaluate
engine.tick(null);
String module = engine.getCurrentModule();

// Reward
RLRewardCalculator rl = new RLRewardCalculator();
if (isCorrect(module, scenario)) {
    rl.rewardCorrectModule(module, true);
} else {
    rl.punishWrongModule(module, "Expected");
}

System.out.println("Result: " + module + ", Score: " + rl.getNetRewards());
```

### Run Full Harness

```java
RandomizedPvPTestHarness harness = new RandomizedPvPTestHarness(
    100,     // 100 runs
    12345L,  // seed
    true     // verbose
);

harness.executeAllRuns();

System.out.printf("Success Rate: %.1f%%\n", harness.getSuccessRate() * 100);
System.out.printf("Module Correctness: %.1f%%\n", 
    harness.getRuns().stream()
        .filter(r -> r.isSuccess())
        .count() * 100.0 / 100);
```

## Performance Tuning

### Improve Module Selection Accuracy

1. **Increase specificity**: Add more scenario-specific conditions
2. **Adjust reward weights**: Higher rewards for correct decisions
3. **Test scenario diversity**: Ensure all scenario types are covered

### Improve Survival Decisions

1. **Lower health threshold**: Trigger defensive earlier (e.g., health < 8.0)
2. **Reward disengagement**: Recognize when to retreat
3. **Punish recklessness**: Higher penalties for overcommitment

### Improve Item Usage

1. **Track potion effects**: Use active potions in signal logic
2. **Inventory awareness**: Check available items in module selection
3. **Timing optimization**: Reward correct shield/totem timing

## Data-Driven Development

1. **Collect baseline**: Run harness and record success rate
2. **Make changes**: Modify DecisionEngine logic
3. **Measure impact**: Re-run harness and compare
4. **Iterate**: Repeat until target success rate is achieved

## Troubleshooting

### Issue: All runs returning wrong module
**Check**:
- DecisionEngine.getCurrentModule() is returning correct value
- evaluateModuleChoice() matches your scenario recommendations
- Module initialization is complete

### Issue: Low survival rate
**Check**:
- Health threshold is appropriate
- Totem usage is triggered at correct health level
- Shield blocking is being recognized

### Issue: High potion wastage
**Check**:
- Potion usage logic considers timing
- Potion effects are correctly detected
- Active effect tracking matches game behavior

## Next Steps

1. Integrate enhanced ContextScanner with existing DecisionEngine
2. Add shield and totem modules if missing
3. Implement decision logic as shown in example
4. Run test harness: `RandomizedPvPTestHarness(100, seed, true).executeAllRuns()`
5. Analyze results and iterate
6. Target > 70% success rate

See `RANDOMIZED_PVP_HARNESS_GUIDE.md` for detailed API reference.
