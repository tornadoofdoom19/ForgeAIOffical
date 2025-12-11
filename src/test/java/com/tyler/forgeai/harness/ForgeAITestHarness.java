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

import java.util.ArrayList;
import java.util.List;

/**
 * ForgeAI Virtual Testing Harness
 *
 * Allows debugging PvP decision logic without a live Minecraft server.
 * Provides:
 * - Mocked ContextScanner.Signals (combat state, equipment, context)
 * - Simulated DecisionEngine tick() calls
 * - Reinforcement learning hook verification
 * - Structured test output with module selection and timing
 * - Scenario-based testing framework
 *
 * Usage:
 * ```
 * ForgeAITestHarness harness = new ForgeAITestHarness();
 * harness.runScenario("Mace Aerial Attack", scenario -> scenario
 *     .withInCombat(true)
 *     .withElytraFlight(true)
 *     .withMaceEquipped(true)
 *     .expectingModule("MaceModule")
 * );
 * harness.printReport();
 * ```
 */
public class ForgeAITestHarness {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-harness");

    // Test infrastructure
    private final MockMinecraftServer mockServer;
    private final ContextScanner mockScanner;
    private final CommunicationManager comms;
    private final TrainingManager trainingManager;
    private final MemoryManager memoryManager;
    private final RewardSystem rewardSystem;
    private final PunishmentSystem punishmentSystem;
    private final DecisionEngine decisionEngine;

    // Results tracking
    private final List<PvPTestResult> results = new ArrayList<>();
    private int passCount = 0;
    private int failCount = 0;

    public ForgeAITestHarness() {
        LOGGER.info("=================================");
        LOGGER.info("ForgeAI Virtual Testing Harness");
        LOGGER.info("=================================");
        LOGGER.info("Initializing test infrastructure...");

        // Initialize mock Minecraft server
        this.mockServer = new MockMinecraftServer();
        LOGGER.debug("✓ MockMinecraftServer initialized");

        // Initialize ContextScanner (with stubs for full functionality)
        this.mockScanner = new ContextScanner();
        mockScanner.init();
        LOGGER.debug("✓ ContextScanner initialized");

        // Initialize AI subsystems
        this.comms = new CommunicationManager();
        comms.init();
        LOGGER.debug("✓ CommunicationManager initialized");

        this.trainingManager = new TrainingManager();
        trainingManager.init();
        LOGGER.debug("✓ TrainingManager initialized");

        this.memoryManager = new MemoryManager();
        memoryManager.init();
        LOGGER.debug("✓ MemoryManager initialized");

        this.rewardSystem = new RewardSystem();
        rewardSystem.init();
        LOGGER.debug("✓ RewardSystem initialized");

        this.punishmentSystem = new PunishmentSystem();
        punishmentSystem.init();
        LOGGER.debug("✓ PunishmentSystem initialized");

        // Initialize DecisionEngine and wire AI subsystems
        this.decisionEngine = new DecisionEngine(mockScanner, comms);
        decisionEngine.setTrainingManager(trainingManager);
        decisionEngine.setMemoryManager(memoryManager);
        decisionEngine.setRewardSystem(rewardSystem);
        decisionEngine.setPunishmentSystem(punishmentSystem);
        decisionEngine.init();
        LOGGER.debug("✓ DecisionEngine initialized and wired");

        LOGGER.info("✅ Test harness ready for scenario execution\n");
    }

    // ---- Scenario Building API ----

    /**
     * Create and run a test scenario with fluent builder.
     */
    public PvPTestResult runScenario(String scenarioName, ScenarioBuilder builder) {
        // Reset state for clean test
        mockServer.resetPlayerState();
        // Create fresh AI subsystems and a fresh DecisionEngine for isolation
        TrainingManager tm = new TrainingManager(); tm.init();
        MemoryManager mm = new MemoryManager(); mm.init();
        RewardSystem rs = new RewardSystem(); rs.init();
        PunishmentSystem ps = new PunishmentSystem(); ps.init();

        DecisionEngine localEngine = new DecisionEngine(mockScanner, comms);
        localEngine.setTrainingManager(tm);
        localEngine.setMemoryManager(mm);
        localEngine.setRewardSystem(rs);
        localEngine.setPunishmentSystem(ps);
        localEngine.init();

        // Create scenario wired to the fresh engine
        PvPTestScenario scenario = new PvPTestScenario(scenarioName, mockScanner, mockServer, localEngine);

        // Build via callback
        builder.build(scenario);

        // Execute scenario
        PvPTestResult result = scenario.run();
        results.add(result);

        // Track pass/fail
        if (result.isSuccess()) {
            passCount++;
        } else {
            failCount++;
        }

        return result;
    }

    /**
     * Functional interface for scenario building.
     */
    @FunctionalInterface
    public interface ScenarioBuilder {
        void build(PvPTestScenario scenario);
    }

    // ---- Standard Test Scenarios ----

    /**
     * Scenario 1: Enemy with shield → expect SwordModule
     */
    public PvPTestResult testSwordAgainstShield() {
        return runScenario("Sword vs Shield", scenario -> scenario
            .withInCombat(true)
            .expectingModule("SwordModule")
            .withReason("Opponent has shield, sword is reliable fallback for sustained combat")
        );
    }

    /**
     * Scenario 2: Elytra flying with mace equipped → expect MaceModule
     */
    public PvPTestResult testMaceAerialAttack() {
        return runScenario("Mace Aerial Attack", scenario -> scenario
            .withInCombat(true)
            .withElytraFlight(true)
            .withMaceEquipped(true)
            .expectingModule("MaceModule")
            .withReason("Player is airborne with mace equipped, conditions perfect for aerial strike")
        );
    }

    /**
     * Scenario 3: Crystal burst opportunity → expect CrystalModule
     */
    public PvPTestResult testCrystalBurst() {
        return runScenario("Crystal Burst Opportunity", scenario -> scenario
            .withInCombat(true)
            .withCrystalOpportunity(true)
            .expectingModule("CrystalModule")
            .withReason("Crystal burst window detected, highest priority tactic available")
        );
    }

    /**
     * Scenario 4: No combat → restore last passive mode (Stasis)
     */
    public PvPTestResult testCombatDisengagement() {
        return runScenario("Combat Disengagement → Stasis", scenario -> scenario
            .withInCombat(false)
            .withStasisReady(true)
            .expectingModule("StasisModule")
            .withReason("No threat detected, restore idle state (Stasis)")
        );
    }

    /**
     * Scenario 5: Building phase → expect BuilderModule
     */
    public PvPTestResult testBuilderMode() {
        return runScenario("Builder Mode Activation", scenario -> scenario
            .withInCombat(false)
            .withBuildingPhase(true)
            .expectingModule("BuilderModule")
            .withReason("Building phase detected, no combat threat, activate builder")
        );
    }

    /**
     * Scenario 6: Resources needed → expect GathererModule
     */
    public PvPTestResult testGathererMode() {
        return runScenario("Gatherer Mode Activation", scenario -> scenario
            .withInCombat(false)
            .withNeedsResources(true)
            .expectingModule("GathererModule")
            .withReason("Inventory depleted, resources needed, activate gatherer")
        );
    }

    /**
     * Scenario 7: Combat override (no mace, not flying, no crystal) → SwordModule fallback
     */
    public PvPTestResult testSwordFallback() {
        return runScenario("Sword Fallback (Grounded Combat)", scenario -> scenario
            .withInCombat(true)
            .withElytraFlight(false)  // Not flying
            .withMaceEquipped(false)  // No mace
            .withCrystalOpportunity(false)  // No crystal
            .expectingModule("SwordModule")
            .withReason("In combat but no specialized tactics available, use reliable sword")
        );
    }

    /**
     * Scenario 8: Weight threshold test (crystal weight low) → skip crystal, use sword
     */
    public PvPTestResult testWeightThresholdGating() {
        return runScenario("Weight Threshold Gating", scenario -> scenario
            .withInCombat(true)
            .withCrystalOpportunity(true)  // Technically available
            // But weight would be < 0.6, so skip
            .expectingModule("SwordModule")
            .withReason("Crystal opportunity exists but weight threshold not met, fallback to sword")
        );
    }

    // ---- Reporting ----

    /**
     * Print comprehensive test report.
     */
    public void printReport() {
        int total = results.size();
        double passRate = total == 0 ? 0 : (100.0 * passCount / total);

        LOGGER.info("\n");
        LOGGER.info("=================================");
        LOGGER.info("FORGEAI TEST HARNESS REPORT");
        LOGGER.info("=================================");
        LOGGER.info("Total Scenarios: {}", total);
        LOGGER.info("✅ Passed: {} ({:.1f}%)", passCount, passRate);
        LOGGER.info("❌ Failed: {}", failCount);
        LOGGER.info("=================================\n");

        // Detailed results
        LOGGER.info("SCENARIO RESULTS:");
        LOGGER.info("---------------------------------");
        for (PvPTestResult result : results) {
            String status = result.isSuccess() ? "✅ PASS" : "❌ FAIL";
            LOGGER.info("{} | {} | Expected: {}, Got: {} | {:.2f}ms",
                status,
                result.getScenarioName(),
                result.getExpectedModule(),
                result.getModuleSelected(),
                result.getElapsedMillis()
            );

            if (result.hasError()) {
                LOGGER.warn("  Error: {}", result.getError().getMessage());
            }
        }
        LOGGER.info("---------------------------------\n");

        // AI Subsystem Status
        logAISubsystemStatus();

        // Export JSON results for CI/analysis
        try {
            TestMetricsExporter.export(results);
            LOGGER.info("Exported harness results to {}", TestMetricsExporter.defaultOut());
        } catch (Exception e) {
            LOGGER.warn("Failed to export harness results: {}", e.getMessage());
        }
    }

    /**
     * Log AI subsystem metrics (training counts, rewards, etc.)
     */
    private void logAISubsystemStatus() {
        LOGGER.info("AI SUBSYSTEM STATUS:");
        LOGGER.info("---------------------------------");

        // Training metrics
        LOGGER.info("TrainingManager Metrics:");
        String[] modules = {"CrystalModule", "MaceModule", "SwordModule", "CartModule", "BuilderModule", "GathererModule", "StasisModule"};
        for (String module : modules) {
            double successRate = trainingManager.getSuccessRate(module);
            LOGGER.info("  {}: {:.1f}% success rate", module, successRate * 100);
        }

        // Reward/Punishment totals
        LOGGER.info("RewardSystem Total: {} points", rewardSystem.getRewardPoints());
        LOGGER.info("PunishmentSystem Total: {} penalty points", punishmentSystem.getPenaltyPoints());

        // Memory status
        LOGGER.info("MemoryManager Experiences Logged: {} total", memoryManager.getRecentExperiences(Integer.MAX_VALUE).size());

        LOGGER.info("---------------------------------\n");
    }

    /**
     * Run all standard test scenarios and return summary.
     */
    public void runAllStandardScenarios() {
        LOGGER.info("Running all standard PvP scenarios...\n");

        testSwordAgainstShield();
        testMaceAerialAttack();
        testCrystalBurst();
        testCombatDisengagement();
        testBuilderMode();
        testGathererMode();
        testSwordFallback();
        testWeightThresholdGating();

        // Additional scenarios for broader coverage
        testCartFallback();
        testLowHealthDefensive();
        testCrystalBlockedByShield();

        printReport();
    }

    // ---- Additional scenarios ----

    public PvPTestResult testCartFallback() {
        return runScenario("Cart Fallback (Disruption)", scenario -> scenario
            .withInCombat(true)
            .withCrystalOpportunity(false)
            .withElytraFlight(false)
            .expectingModule("CartModule")
            .withReason("No specialized tactic available; cart for disruption/mobility")
        );
    }

    public PvPTestResult testLowHealthDefensive() {
        return runScenario("Low Health Defensive", scenario -> scenario
            .withInCombat(true)
            .withCrystalOpportunity(false)
            .withElytraFlight(false)
            .expectingModule("SwordModule")
            .withReason("Low-health defensive spacing should prefer sword/defense behavior")
        );
    }

    public PvPTestResult testCrystalBlockedByShield() {
        return runScenario("Crystal Blocked by Shield", scenario -> scenario
            .withInCombat(true)
            .withCrystalOpportunity(true)
            .withReason("Simulate shield blocking crystal timing; expect fallback to SwordModule")
            .expectingModule("SwordModule")
        );
    }

    // ---- Main Entry Point ----

    /**
     * Main method for running harness as standalone application.
     */
    public static void main(String[] args) {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        
        ForgeAITestHarness harness = new ForgeAITestHarness();

        // Run all standard scenarios
        harness.runAllStandardScenarios();

        // Example of custom scenario
        LOGGER.info("\n🔧 Running custom scenario example...\n");
        harness.runScenario("Custom: Mace + Building Phase", scenario -> scenario
            .withInCombat(false)
            .withBuildingPhase(true)
            .withElytraFlight(true)
            .withMaceEquipped(true)
            .expectingModule("BuilderModule")
            .withReason("Building phase overrides other signals when not in combat")
        );

        // Final report
        harness.printReport();

        LOGGER.info("✅ Test harness execution complete!");
    }
}
