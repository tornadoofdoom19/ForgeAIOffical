# Randomized PvP Test Harness - Quick Start

## 5-Minute Setup

### 1. Build the Project
```bash
cd /workspaces/ForgeAI0.0.1Indev
./gradlew build
```

### 2. Run Basic Test (10 runs)
```bash
./gradlew test --tests "RandomizedPvPTestHarness"
```

### 3. Run Custom Configuration
```bash
# 50 runs, seed 99999, verbose output
java -cp build/classes/java/test com.tyler.forgeai.harness.RandomizedPvPTestHarness 50 99999 true
```

## Core Classes

### Generate Random Inventory
```java
RandomizedPvPInventory inventory = new RandomizedPvPInventory(12345L);
inventory.randomize();

System.out.println(inventory.summarizeInventory());
// Output:
// === Inventory Summary ===
// Health: 18.5/20 | Food: 15/20 | Armor Durability: 72%
// Items:
//   - Sword: x1 (95% durability)
//   - Shield: x1 (88% durability)
//   - Totem of Undying: x2
//   - Ender Pearl: x8
//   - Potions: x3
// Active Potions:
//   - Strength
//   - Speed
```

### Generate Random Scenario
```java
RandomizedPvPScenarioGenerator gen = new RandomizedPvPScenarioGenerator(12345L);
RandomizedPvPScenarioGenerator.PvPScenarioState scenario = gen.generate();

System.out.println(scenario.summarize());
// Output:
// === Scenario: Mace Aerial Attack ===
// Combat: YES
// Opponent: AGGRESSIVE
// Opponent: Airborne
// Opponent Health: 14.2/20
// Opponent Distance: 6.5 blocks
// Incoming Melee Damage: 11.2
// Recommended Items: Elytra, Mace
// Recommended Action: MaceModule with elytra flight for counter-aerial or shield for defense
```

### Generate Command Signals
```java
List<CommandSignal> signals = CommandSignal.generateSignalsFromState(inventory, scenario);

for (CommandSignal signal : signals) {
    System.out.println(signal);
    // Output:
    // [SIGNAL] Signal: Opponent Airborne (priority=0.70) from ContextScanner
    // [SIGNAL] Signal: Potion Available (priority=0.50) from ContextScanner
}
```

### Calculate Reinforcement Learning
```java
RLRewardCalculator rl = new RLRewardCalculator();

// Simulate correct decision
rl.rewardCorrectModule("MaceModule", true);
rl.rewardSurvival(18.0f, 8.0f);

// Simulate mistake
rl.punishWrongModule("SwordModule", "MaceModule");

System.out.println(rl.summarizeSession());
// Output:
// === Reinforcement Learning Session Summary ===
// Total Rewards: 130 points
// Total Punishments: 40 points
// Net: 90 points
// Success Score: 76.47%
```

### Run Full Test Harness
```java
RandomizedPvPTestHarness harness = new RandomizedPvPTestHarness(100, 12345L, true);
harness.executeAllRuns();

System.out.printf("Success Rate: %.1f%%\n", harness.getSuccessRate() * 100);
// Output:
// Success Rate: 67.0%
```

## Output Examples

### Verbose Run Output
```
===== Run 1 of 100 (seed: 12346) =====
=== Inventory Summary ===
Health: 19.2/20 | Food: 18/20 | Armor Durability: 81%
Items:
  - Sword: x1 (92% durability)
  - Shield: x1 (78% durability)
  - Totem of Undying: x3
  - Ender Pearl: x12
Active Potions:
  - Strength
  - Speed

=== Scenario: Aggressive Rush ===
Combat: YES
Opponent: AGGRESSIVE
Opponent Distance: 4.2 blocks
Incoming Melee Damage: 6.5
Recommended Action: SwordModule or MaceModule for melee counter

✅ RUN PASS: Module=SwordModule, Net Reward=85, Time=11.34ms
```

### Final Summary
```
=================================================
Test Harness Summary
=================================================
Total Runs: 100
Successes: 67 (67.0%)
Failures: 33 (33.0%)
Module Correctness: 68.50%
Avg Signals/Run: 5
Shield Usage Correct: 12/100
Totem Usage Correct: 8/100
Avg Items/Run: 8
Total RL Events: 450

Top Rewards:
  ✓ [SURVIVAL] +150: Health preserved: 19.5, Damage blocked: 12.0
  ✓ [CORRECT_MODULE] +50: MaceModule selected in optimal conditions
  ✓ [SHIELD_BLOCK] +80: Blocked 8.0 damage with correct timing

Top Punishments:
  ✗ [DEATH] -300: Player died: Lethal damage imminent
  ✗ [RECKLESS] -200: Overcommitted with 2.5 health remaining
  ✗ [WRONG_MODULE] -40: Selected SwordModule instead of MaceModule
=================================================
```

## Key Metrics to Track

| Metric | Target | Meaning |
|--------|--------|---------|
| **Success Rate** | > 70% | % of runs with positive rewards |
| **Module Correctness** | > 75% | % of correct module selections |
| **Shield Usage** | > 60% | % of correct shield defensive plays |
| **Totem Usage** | > 70% | % of correct totem survival plays |
| **Avg Signal Count** | 4-6 | Reasonable number of decision factors |
| **Net Reward/Run** | > 50 | Positive learning trend |

## Troubleshooting

### Issue: All runs failing
**Solution**: Check DecisionEngine module initialization
```java
DecisionEngine engine = new DecisionEngine(scanner, comms);
engine.init(); // Important!
```

### Issue: Rewards too low
**Solution**: Increase reward values in RLRewardCalculator
```java
private static final int REWARD_SURVIVAL_BONUS = 150; // Was 100
```

### Issue: Wrong scenarios for inventory
**Solution**: Verify signal generation matches inventory
```java
List<CommandSignal> signals = CommandSignal.generateSignalsFromState(inventory, scenario);
// Check signals match scenario requirements
```

## Advanced Usage

### Custom Test Suite
```java
// Test specific scenario type
for (int i = 0; i < 10; i++) {
    RandomizedPvPScenarioGenerator gen = new RandomizedPvPScenarioGenerator(i);
    RandomizedPvPScenarioGenerator.PvPScenarioState scenario = gen.generate();
    
    if (scenario.scenarioType == RandomizedPvPScenarioGenerator.ScenarioType.AGGRESSIVE_CRYSTAL) {
        // Run test on crystal scenarios only
        testScenario(scenario);
    }
}
```

### Filter by Scenario Type
```java
List<RandomizedPvPTestHarness.RandomizedPvPTestRun> runs = harness.getRuns();
runs.stream()
    .filter(r -> r.scenario.crystalOpportunity)
    .forEach(r -> System.out.println(r.selectedModule));
```

### Analyze RL Events
```java
List<RLRewardCalculator.RLEvent> events = harness.getAllEvents();
int totalRewards = events.stream()
    .filter(RLRewardCalculator.RLEvent::isReward)
    .mapToInt(e -> e.points)
    .sum();
System.out.println("Total Rewards Across All Runs: " + totalRewards);
```

## Expected Performance

| Configuration | Time | Success Rate |
|--------------|------|--------------|
| 10 runs | ~110ms | 60-75% |
| 50 runs | ~550ms | 60-75% |
| 100 runs | ~1.1s | 60-75% |
| 1000 runs | ~11s | 60-75% |

Success rate depends on DecisionEngine quality and reward tuning.

## Next Steps

1. ✅ Run initial harness: `RandomizedPvPTestHarness(100, 12345L, true).executeAllRuns()`
2. 📊 Analyze success metrics
3. 🎯 Identify failing scenario types
4. 🔧 Tune DecisionEngine based on results
5. 🚀 Iterate and improve
6. 📈 Track progress over time with different seeds

## File Structure

```
ForgeAI0.0.1Indev/
├── src/test/java/com/tyler/forgeai/harness/
│   ├── RandomizedPvPInventory.java           ← Item & potion randomization
│   ├── RandomizedPvPScenarioGenerator.java   ← Combat scenario generation
│   ├── CommandSignal.java                    ← Direct/indirect signals
│   ├── RLRewardCalculator.java               ← Reinforcement learning
│   ├── RandomizedPvPTestHarness.java         ← Main orchestrator
│   ├── ForgeAITestHarness.java               ← Original harness (reference)
│   ├── PvPTestScenario.java                  ← Scenario builder
│   └── ...
├── src/main/java/com/tyler/forgeai/core/
│   ├── ContextScanner.java                   ← Enhanced with full combat state
│   ├── DecisionEngine.java                   ← AI decision maker
│   └── ...
├── RANDOMIZED_PVP_HARNESS_GUIDE.md           ← Full documentation
└── QUICKSTART_RANDOMIZED_PVP.md              ← This file
```

## Support & Questions

For detailed documentation, see: `RANDOMIZED_PVP_HARNESS_GUIDE.md`

Key topics:
- Architecture overview
- API reference
- Integration examples
- Scenario specifications
- Customization guide
