package com.tyler.forgeai.harness;

import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.core.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scenario builder for PvP testing.
 * Allows fluent construction of test scenarios with mocked Signals and DecisionEngine state.
 */
public class PvPTestScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-scenario");

    private final String name;
    private final ContextScanner mockScanner;
    private final MockMinecraftServer mockServer;
    private final DecisionEngine decisionEngine;

    // Scenario state
    private boolean inCombat = false;
    private boolean isFlyingWithElytra = false;
    private boolean hasMaceEquipped = false;
    private boolean crystalOpportunity = false;
    private boolean needsResources = false;
    private boolean isBuildingPhase = false;
    private boolean shouldEnterStasis = true;

    // Expected outcome
    private String expectedModule = null;
    private String expectedReason = null;

    public PvPTestScenario(String name, ContextScanner mockScanner, MockMinecraftServer mockServer, DecisionEngine decisionEngine) {
        this.name = name;
        this.mockScanner = mockScanner;
        this.mockServer = mockServer;
        this.decisionEngine = decisionEngine;
    }

    // ---- Fluent Builder API ----

    public PvPTestScenario withInCombat(boolean inCombat) {
        this.inCombat = inCombat;
        if (inCombat) {
            mockServer.getPrimaryPlayer().setLastHurtBy(new Object()); // Mark as attacked
        } else {
            mockServer.getPrimaryPlayer().clearLastHurtBy();
        }
        return this;
    }

    public PvPTestScenario withElytraFlight(boolean flying) {
        this.isFlyingWithElytra = flying;
        mockServer.getPrimaryPlayer().setFallFlying(flying);
        return this;
    }

    public PvPTestScenario withMaceEquipped(boolean equipped) {
        this.hasMaceEquipped = equipped;
        if (equipped) {
            mockServer.getPrimaryPlayer().setMainHandItem("mace");
        }
        return this;
    }

    public PvPTestScenario withCrystalOpportunity(boolean available) {
        this.crystalOpportunity = available;
        return this;
    }

    public PvPTestScenario withNeedsResources(boolean needs) {
        this.needsResources = needs;
        return this;
    }

    public PvPTestScenario withBuildingPhase(boolean building) {
        this.isBuildingPhase = building;
        return this;
    }

    public PvPTestScenario withStasisReady(boolean ready) {
        this.shouldEnterStasis = ready;
        return this;
    }

    public PvPTestScenario expectingModule(String moduleName) {
        this.expectedModule = moduleName;
        return this;
    }

    public PvPTestScenario withReason(String reason) {
        this.expectedReason = reason;
        return this;
    }

    // ---- Execution ----

    /**
     * Run the scenario and return result.
     */
    public PvPTestResult run() {
        LOGGER.info("=== Running Scenario: {} ===", name);
        LOGGER.debug("State: inCombat={}, flying={}, mace={}, crystal={}, resources={}, building={}", 
            inCombat, isFlyingWithElytra, hasMaceEquipped, crystalOpportunity, needsResources, isBuildingPhase);

        // Build mocked Signals object
        ContextScanner.Signals signals = createMockedSignals();

        // Simulate tick
        long startTime = System.nanoTime();
        String moduleSelected = null;
        Throwable error = null;
        try {
            // Intercept module selection by temporarily wrapping decision engine
            moduleSelected = simulateDecisionEngineTick(signals);
        } catch (Exception e) {
            error = e;
            LOGGER.error("Error during scenario execution", e);
        }
        long elapsed = System.nanoTime() - startTime;

        // Build result
        PvPTestResult result = new PvPTestResult(
            name,
            signals,
            moduleSelected,
            expectedModule,
            expectedReason,
            elapsed,
            error
        );

        logResult(result);
        return result;
    }

    /**
     * Create mocked Signals object for this scenario.
     * This reflects the scenario state without requiring real Minecraft player.
     */
    private ContextScanner.Signals createMockedSignals() {
        // Build a synthetic Signals instance using the test-friendly factory
        return ContextScanner.Signals.synthetic(
            inCombat,
            isFlyingWithElytra,
            hasMaceEquipped,
            crystalOpportunity,
            needsResources,
            isBuildingPhase,
            shouldEnterStasis
        );
    }

    /**
     * Simulate calling DecisionEngine.tick() and capture which module was selected.
     * This is a simplified simulation that logs module selection without full game context.
     */
    private String simulateDecisionEngineTick(ContextScanner.Signals signals) {
        // Push synthetic signals into the ContextScanner used by the DecisionEngine
        mockScanner.setLastSignals(signals);

        // Call the real DecisionEngine tick (we pass null MinecraftServer; the
        // ContextScanner will return the last synthetic signals when server==null).
        decisionEngine.tick(null);

        // Read back the module that DecisionEngine recorded
        String selected = decisionEngine.getCurrentModule();
        LOGGER.info("DecisionEngine selected module: {}", selected);
        return selected;
    }

    /**
     * Log scenario result.
     */
    private void logResult(PvPTestResult result) {
        if (result.isSuccess()) {
            LOGGER.info("✅ PASS: {} | Expected: {}, Got: {}", 
                result.getScenarioName(), expectedModule, result.getModuleSelected());
        } else {
            LOGGER.warn("❌ FAIL: {} | Expected: {}, Got: {}", 
                result.getScenarioName(), expectedModule, result.getModuleSelected());
        }
        LOGGER.debug("Elapsed: {:.2f}ms", result.getElapsedMillis());
    }

    @Override
    public String toString() {
        return "PvPTestScenario{" +
                "name='" + name + '\'' +
                ", inCombat=" + inCombat +
                ", flying=" + isFlyingWithElytra +
                ", mace=" + hasMaceEquipped +
                ", crystal=" + crystalOpportunity +
                ", expectedModule='" + expectedModule + '\'' +
                '}';
    }
}
