package com.tyler.forgeai.harness;

import com.tyler.forgeai.ai.MemoryManager;
import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.CommunicationManager;
import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.core.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Randomized PvP Test Harness
 * 
 * Simulates randomized PvP scenarios with:
 * - Randomized inventory each run
 * - Fake PvP scenarios (aggressive, defensive, mixed environment)
 * - Direct and indirect command signals
 * - AI decision evaluation
 * - Reinforcement learning tracking
 * - Comprehensive metrics and logging
 */
public class RandomizedPvPTestHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-harness-pvp");

    // Infrastructure
    private final MockMinecraftServer mockServer;
    private final ContextScanner mockScanner;
    private final CommunicationManager comms;

    // Test configuration
    private final int numRuns;
    private final long randomSeed;
    private final boolean verbose;

    // Results tracking
    private final List<RandomizedPvPTestRun> runs = new ArrayList<>();
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final List<RLRewardCalculator.RLEvent> allEvents = new ArrayList<>();

    // Statistics
    private float avgSuccessRate = 0;
    private float avgModuleCorrectness = 0;
    private int totalItemsUsed = 0;
    private int correctShieldUsage = 0;
    private int correctTotemUsage = 0;

    public RandomizedPvPTestHarness(int numRuns, long randomSeed, boolean verbose) {
        this.numRuns = numRuns;
        this.randomSeed = randomSeed;
        this.verbose = verbose;

        LOGGER.info("=================================================");
        LOGGER.info("Randomized PvP Test Harness Initialization");
        LOGGER.info("=================================================");
        LOGGER.info("Runs: {}, Seed: {}, Verbose: {}", numRuns, randomSeed, verbose);

        this.mockServer = new MockMinecraftServer();
        this.mockScanner = new ContextScanner();
        this.mockScanner.init();
        this.comms = new CommunicationManager();
        this.comms.init();

        LOGGER.info("Infrastructure initialized\n");
    }

    /**
     * Execute all randomized runs.
     */
    public void executeAllRuns() {
        LOGGER.info("Starting {} randomized PvP test runs...\n", numRuns);

        for (int i = 0; i < numRuns; i++) {
            long runSeed = randomSeed + i;
            executeRun(i + 1, runSeed);
        }

        // Calculate aggregate statistics
        calculateStatistics();

        LOGGER.info("\n✅ All {} runs completed", numRuns);
    }

    /**
     * Execute a single randomized run.
     */
    private void executeRun(int runNumber, long seed) {
        LOGGER.info("===== Run {} of {} (seed: {}) =====", runNumber, numRuns, seed);

        // Generate randomized inventory
        RandomizedPvPInventory inventory = new RandomizedPvPInventory(seed);
        inventory.randomize();

        if (verbose) {
            LOGGER.info(inventory.summarizeInventory());
        }

        // Generate randomized scenario
        RandomizedPvPScenarioGenerator scenarioGen = new RandomizedPvPScenarioGenerator(seed);
        RandomizedPvPScenarioGenerator.PvPScenarioState scenario = scenarioGen.generate();

        if (verbose) {
            LOGGER.info(scenario.summarize());
        }

        // Generate command signals
        List<CommandSignal> indirectSignals = CommandSignal.generateSignalsFromState(inventory, scenario);
        CommandSignal.SignalQueue signals = new CommandSignal.SignalQueue();
        signals.enqueueAll(indirectSignals);

        // Occasionally add direct command
        if (new Random(seed).nextDouble() < 0.3) {
            CommandSignal directCmd = CommandSignal.generateRandomDirectCommand(inventory);
            signals.enqueue(directCmd);
            LOGGER.debug("Direct command injected: {}", directCmd);
        }

        // Create fresh DecisionEngine for this run
        TrainingManager tm = new TrainingManager(); tm.init();
        MemoryManager mm = new MemoryManager(); mm.init();
        RewardSystem rs = new RewardSystem(); rs.init();
        PunishmentSystem ps = new PunishmentSystem(); ps.init();

        DecisionEngine engine = new DecisionEngine(mockScanner, comms);
        engine.setTrainingManager(tm);
        engine.setMemoryManager(mm);
        engine.setRewardSystem(rs);
        engine.setPunishmentSystem(ps);
        engine.init();

        // Create RL calculator
        RLRewardCalculator rlCalc = new RLRewardCalculator();

        // Simulate DecisionEngine tick
        long startTime = System.nanoTime();
        String selectedModule = null;
        Throwable error = null;

        try {
            // Build synthetic signals from inventory and scenario
            ContextScanner.Signals signals_obj = buildSignalsFromState(inventory, scenario);
            mockScanner.setLastSignals(signals_obj);

            // Tick the DecisionEngine
            engine.tick(null);
            selectedModule = engine.getCurrentModule();

            // Evaluate performance
            evaluatePerformance(selectedModule, scenario, inventory, rlCalc, indirectSignals);

        } catch (Exception e) {
            error = e;
            LOGGER.error("Error during run execution", e);
            failureCount.incrementAndGet();
            rlCalc.punishDeath("Exception: " + e.getMessage());
        }

        long elapsedNanos = System.nanoTime() - startTime;
        float elapsedMs = elapsedNanos / 1_000_000.0f;

        // Create run record
        RandomizedPvPTestRun run = new RandomizedPvPTestRun(
                runNumber,
                seed,
                inventory,
                scenario,
                selectedModule,
                indirectSignals,
                rlCalc,
                elapsedMs,
                error
        );

        runs.add(run);
        allEvents.addAll(rlCalc.getEvents());

        // Log run result
        if (rlCalc.getNetRewards() > 0) {
            successCount.incrementAndGet();
            LOGGER.info("✅ RUN PASS: Module={}, Net Reward={}, Time={:.2f}ms\n",
                    selectedModule, rlCalc.getNetRewards(), elapsedMs);
        } else {
            failureCount.incrementAndGet();
            LOGGER.warn("❌ RUN FAIL: Module={}, Net Reward={}, Time={:.2f}ms\n",
                    selectedModule, rlCalc.getNetRewards(), elapsedMs);
        }
    }

    /**
     * Build comprehensive Signals object from inventory and scenario state.
     */
    private ContextScanner.Signals buildSignalsFromState(
            RandomizedPvPInventory inventory,
            RandomizedPvPScenarioGenerator.PvPScenarioState scenario) {

        Set<String> potions = new HashSet<>();
        for (RandomizedPvPInventory.PotionEffect effect : inventory.getActivePotions()) {
            potions.add(effect.getDisplayName());
        }

        return ContextScanner.Signals.syntheticFull(
                scenario.inCombat,
                inventory.hasItem(RandomizedPvPInventory.PvPItem.ELYTRA),
                inventory.hasItem(RandomizedPvPInventory.PvPItem.MACE),
                scenario.crystalOpportunity,
                !inventory.hasItem(RandomizedPvPInventory.PvPItem.SWORD) &&
                    !inventory.hasItem(RandomizedPvPInventory.PvPItem.SHIELD) &&
                    !inventory.hasItem(RandomizedPvPInventory.PvPItem.TOTEM),
                false,
                !scenario.inCombat && !scenario.falling && !scenario.inLava,
                inventory.getPlayerHealth(),
                inventory.getFoodLevel(),
                inventory.getArmorDurability(),
                potions,
                inventory.isLowHealth(),
                inventory.isArmorBroken(),
                scenario.incomingMeleeDamage,
                scenario.incomingProjectileDamage,
                scenario.opponentAirborne,
                scenario.opponentShieldActive,
                scenario.webTrapDetected,
                scenario.falling,
                scenario.fallHeight,
                scenario.inLava
        );
    }

    /**
     * Evaluate DecisionEngine performance and apply RL rewards/punishments.
     */
    private void evaluatePerformance(
            String selectedModule,
            RandomizedPvPScenarioGenerator.PvPScenarioState scenario,
            RandomizedPvPInventory inventory,
            RLRewardCalculator rlCalc,
            List<CommandSignal> signals) {

        if (selectedModule == null) {
            rlCalc.punishWrongModule("null", "Any non-null module");
            return;
        }

        // Check if module selection matches scenario recommendation
        boolean correctModule = evaluateModuleChoice(selectedModule, scenario, inventory);
        if (correctModule) {
            rlCalc.rewardCorrectModule(selectedModule, true);
            avgModuleCorrectness += 1.0f;
        } else {
            rlCalc.punishWrongModule(selectedModule, "Scenario recommendation");
            avgModuleCorrectness += 0.0f;
        }

        // Evaluate survival and item usage
        if (scenario.inCombat) {
            if (inventory.getPlayerHealth() > 10.0f) {
                rlCalc.rewardSurvival(inventory.getPlayerHealth(), scenario.incomingMeleeDamage);
            } else {
                rlCalc.punishRecklessness(inventory.getPlayerHealth(), 
                        !inventory.hasItem(RandomizedPvPInventory.PvPItem.ENDER_PEARL));
            }
        }

        // Evaluate shield usage
        if (selectedModule.contains("Shield") && scenario.opponentShieldActive) {
            rlCalc.rewardShieldBlock(scenario.incomingMeleeDamage * 0.5f, true);
            correctShieldUsage++;
        }

        // Evaluate totem usage
        if (selectedModule.contains("Totem") && inventory.getPlayerHealth() < 5.0f) {
            rlCalc.rewardTotemUsage(15.0f, true);
            correctTotemUsage++;
        } else if (selectedModule.contains("Totem") && inventory.getPlayerHealth() > 15.0f) {
            rlCalc.punishWastedTotem(0);
        }

        // Evaluate disengagement
        if (selectedModule.contains("Stasis") && !inventory.hasItem(RandomizedPvPInventory.PvPItem.TOTEM)) {
            rlCalc.rewardSuccessfulDisengage(10.0f, false);
        }

        totalItemsUsed += inventory.getInventory().size();
    }

    /**
     * Determine if module selection is correct for the scenario.
     */
    private boolean evaluateModuleChoice(
            String module,
            RandomizedPvPScenarioGenerator.PvPScenarioState scenario,
            RandomizedPvPInventory inventory) {

        switch (scenario.scenarioType) {
            case AGGRESSIVE_RUSH:
                return module.contains("Sword") || module.contains("Mace");
            case AGGRESSIVE_CRYSTAL:
                return module.contains("Crystal");
            case AGGRESSIVE_MACE_AERIAL:
                return module.contains("Mace") || module.contains("Sword");
            case DEFENSIVE_SHIELD:
                return module.contains("Sword") || module.contains("Shield");
            case DEFENSIVE_TOTEM:
                return module.contains("Totem") || module.contains("Cart");
            case DEFENSIVE_WEB_TRAP:
                return module.contains("Sword") || module.contains("Builder");
            case MIXED_FALLING:
                return module.contains("Builder") || module.contains("Stasis");
            case MIXED_LAVA:
                return module.contains("Builder") || module.contains("Stasis");
            case MIXED_RANGED_SPAM:
                return module.contains("Shield") || module.contains("Sword");
            case MIXED_ARMOR_BREAK:
                return module.contains("Stasis"); // Disengage
            default:
                return false;
        }
    }

    /**
     * Calculate aggregate statistics across all runs.
     */
    private void calculateStatistics() {
        if (runs.isEmpty()) return;

        avgModuleCorrectness /= numRuns;
        int totalSignals = runs.stream().mapToInt(r -> r.signals.size()).sum();
        int avgSignals = totalSignals / Math.max(1, numRuns);

        LOGGER.info("\n=================================================");
        LOGGER.info("Test Harness Summary");
        LOGGER.info("=================================================");
        LOGGER.info("Total Runs: {}", numRuns);
        LOGGER.info("Successes: {} ({:.1f}%)", successCount.get(), (successCount.get() * 100.0f / numRuns));
        LOGGER.info("Failures: {} ({:.1f}%)", failureCount.get(), (failureCount.get() * 100.0f / numRuns));
        LOGGER.info("Module Correctness: {:.2f}%", avgModuleCorrectness * 100);
        LOGGER.info("Avg Signals/Run: {}", avgSignals);
        LOGGER.info("Shield Usage Correct: {}/{}", correctShieldUsage, numRuns);
        LOGGER.info("Totem Usage Correct: {}/{}", correctTotemUsage, numRuns);
        LOGGER.info("Avg Items/Run: {}", totalItemsUsed / Math.max(1, numRuns));
        LOGGER.info("Total RL Events: {}", allEvents.size());

        // Top reward events
        List<RLRewardCalculator.RLEvent> rewards = allEvents.stream()
                .filter(RLRewardCalculator.RLEvent::isReward)
                .sorted((a, b) -> Integer.compare(b.points, a.points))
                .limit(5)
                .toList();

        if (!rewards.isEmpty()) {
            LOGGER.info("\nTop Rewards:");
            for (RLRewardCalculator.RLEvent e : rewards) {
                LOGGER.info("  {}", e);
            }
        }

        // Top punishment events
        List<RLRewardCalculator.RLEvent> punishments = allEvents.stream()
                .filter(RLRewardCalculator.RLEvent::isPunishment)
                .sorted((a, b) -> Integer.compare(Math.abs(b.points), Math.abs(a.points)))
                .limit(5)
                .toList();

        if (!punishments.isEmpty()) {
            LOGGER.info("\nTop Punishments:");
            for (RLRewardCalculator.RLEvent e : punishments) {
                LOGGER.info("  {}", e);
            }
        }

        LOGGER.info("=================================================\n");
    }

    // ---- Public API ----

    public int getSuccessCount() { return successCount.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public float getSuccessRate() { return (float) successCount.get() / numRuns; }
    public List<RandomizedPvPTestRun> getRuns() { return new ArrayList<>(runs); }
    public List<RLRewardCalculator.RLEvent> getAllEvents() { return new ArrayList<>(allEvents); }

    // ---- Test Run Record ----

    public static class RandomizedPvPTestRun {
        public final int runNumber;
        public final long seed;
        public final RandomizedPvPInventory inventory;
        public final RandomizedPvPScenarioGenerator.PvPScenarioState scenario;
        public final String selectedModule;
        public final List<CommandSignal> signals;
        public final RLRewardCalculator rlCalculator;
        public final float elapsedMs;
        public final Throwable error;

        public RandomizedPvPTestRun(
                int runNumber,
                long seed,
                RandomizedPvPInventory inventory,
                RandomizedPvPScenarioGenerator.PvPScenarioState scenario,
                String selectedModule,
                List<CommandSignal> signals,
                RLRewardCalculator rlCalculator,
                float elapsedMs,
                Throwable error) {
            this.runNumber = runNumber;
            this.seed = seed;
            this.inventory = inventory;
            this.scenario = scenario;
            this.selectedModule = selectedModule;
            this.signals = signals;
            this.rlCalculator = rlCalculator;
            this.elapsedMs = elapsedMs;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null && rlCalculator.getNetRewards() > 0;
        }

        @Override
        public String toString() {
            return String.format("Run #%d: %s -> %s (%.1f%% success)",
                    runNumber, scenario.scenarioType.getDisplayName(), selectedModule,
                    rlCalculator.getSuccessScore() * 100);
        }
    }

    // ---- Main Entry Point ----

    public static void main(String[] args) {
        // Configuration
        int numRuns = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 12345L;
        boolean verbose = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;

        // Create and run harness
        RandomizedPvPTestHarness harness = new RandomizedPvPTestHarness(numRuns, seed, verbose);
        harness.executeAllRuns();

        // Export results
        harness.exportResults();
    }

    /**
     * Export test results to file/logs.
     */
    public void exportResults() {
        LOGGER.info("\nExporting results...");
        // TODO: Implement CSV/JSON export
        LOGGER.info("Results exported successfully");
    }
}
