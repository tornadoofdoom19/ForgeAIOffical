package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.MemoryManager;
import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.modules.builder.BuilderModule;
import com.tyler.forgeai.modules.gatherer.GathererModule;
import com.tyler.forgeai.modules.pvp.CartModule;
import com.tyler.forgeai.modules.pvp.CrystalModule;
import com.tyler.forgeai.modules.pvp.MaceModule;
import com.tyler.forgeai.modules.pvp.SwordModule;
import com.tyler.forgeai.modules.stasis.StasisModule;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central AI coordinator:
 * - Samples context
 * - Selects active mode(s)
 * - Routes ticks to modules
 * - Reacts instantly to threats (combat override) and restores last passive mode when safe
 */
public class DecisionEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-decision");

    // Dependencies
    private final ContextScanner scanner;
    private final CommunicationManager comms;

    // Optional AI subsystems (set via setters)
    private TrainingManager trainingManager;
    private MemoryManager memoryManager;
    private RewardSystem rewardSystem;
    private PunishmentSystem punishmentSystem;

    // Modules
    private final MaceModule maceModule = new MaceModule();
    private final SwordModule swordModule = new SwordModule();
    private final CrystalModule crystalModule = new CrystalModule();
    private final CartModule cartModule = new CartModule();
    private final BuilderModule builderModule = new BuilderModule();
    private final GathererModule gathererModule = new GathererModule();
    private final StasisModule stasisModule = new StasisModule();

    // Mode flags
    private boolean combatMode = false;
    private boolean builderMode = false;
    private boolean gathererMode = false;
    private boolean stasisMode = true; // default idle

    // Passive mode memory for restoration
    private enum PassiveMode { BUILDER, GATHERER, STASIS, NONE }
    private PassiveMode lastPassiveMode = PassiveMode.STASIS;

    public DecisionEngine(ContextScanner scanner, CommunicationManager comms) {
        this.scanner = scanner;
        this.comms = comms;
    }

    public void init() {
        LOGGER.info("DecisionEngine initialized.");
        // Initialize modules
        maceModule.init();
        swordModule.init();
        crystalModule.init();
        cartModule.init();
        builderModule.init();
        gathererModule.init();
        stasisModule.init();
        // Align module activity with initial modes
        applyModuleFlags();
    }

    public void tick(MinecraftServer server) {
        ContextScanner.Signals s = scanner.sample(server);
        if (s.player == null) {
            ensureOnlyStasisActive();
            stasisModule.tick(s);
            return;
        }

        // Instant combat override: fight if threatened, otherwise restore passive
        if (s.inCombat()) {
            if (!combatMode) enterCombatFromPassive();
        } else {
            if (combatMode) exitCombatToLastPassive();
        }

        // Route ticks based on current mode
        if (combatMode) {
            tickCombatSuite(s);
        } else {
            tickPassiveSuite(s);
        }
    }

    // ---- AI subsystem setters ------------------------------------------------

    public void setTrainingManager(TrainingManager tm) { this.trainingManager = tm; }
    public void setMemoryManager(MemoryManager mm)     { this.memoryManager = mm; }
    public void setRewardSystem(RewardSystem rs)       { this.rewardSystem = rs; }
    public void setPunishmentSystem(PunishmentSystem ps){ this.punishmentSystem = ps; }

    // ---- Mode management API -------------------------------------------------

    public void enableCombatMode(boolean enabled) {
        if (enabled == combatMode) return;
        if (enabled) {
            rememberCurrentPassive();
            combatMode = true;
            disablePassiveModes();
        } else {
            combatMode = false;
            restoreLastPassive();
        }
        applyModuleFlags();
        LOGGER.info("Combat mode: {}", combatMode);
    }

    public void enableBuilderMode(boolean enabled) {
        if (enabled == builderMode) return;
        if (enabled) {
            setPassiveMode(PassiveMode.BUILDER);
            combatMode = false;
        } else {
            builderMode = false;
            if (!anyPassiveActive()) stasisMode = true;
        }
        applyModuleFlags();
        LOGGER.info("Builder mode: {}", builderMode);
    }

    public void enableGathererMode(boolean enabled) {
        if (enabled == gathererMode) return;
        if (enabled) {
            setPassiveMode(PassiveMode.GATHERER);
            combatMode = false;
        } else {
            gathererMode = false;
            if (!anyPassiveActive()) stasisMode = true;
        }
        applyModuleFlags();
        LOGGER.info("Gatherer mode: {}", gathererMode);
    }

    public void enableStasisMode(boolean enabled) {
        if (enabled == stasisMode) return;
        stasisMode = enabled;
        if (enabled) {
            setPassiveMemory(PassiveMode.STASIS);
            combatMode = false;
        }
        applyModuleFlags();
        LOGGER.info("Stasis mode: {}", stasisMode);
    }

    public boolean isCombatMode()   { return combatMode; }
    public boolean isBuilderMode()  { return builderMode; }
    public boolean isGathererMode() { return gathererMode; }
    public boolean isStasisMode()   { return stasisMode; }

    // ---- Internal orchestration ---------------------------------------------

    private void enterCombatFromPassive() {
        rememberCurrentPassive();
        combatMode = true;
        disablePassiveModes();
        applyModuleFlags();
        LOGGER.info("Reactive switch: passive -> combat.");
    }

    private void exitCombatToLastPassive() {
        combatMode = false;
        restoreLastPassive();
        applyModuleFlags();
        LOGGER.info("Threat resolved: restoring last passive mode -> {}", lastPassiveMode);
    }

    private void rememberCurrentPassive() {
        if (builderMode) setPassiveMemory(PassiveMode.BUILDER);
        else if (gathererMode) setPassiveMemory(PassiveMode.GATHERER);
        else if (stasisMode) setPassiveMemory(PassiveMode.STASIS);
        else setPassiveMemory(PassiveMode.NONE);
    }

    private void restoreLastPassive() {
        switch (lastPassiveMode) {
            case BUILDER -> { builderMode = true; gathererMode = false; stasisMode = false; }
            case GATHERER -> { builderMode = false; gathererMode = true; stasisMode = false; }
            case STASIS -> { builderMode = false; gathererMode = false; stasisMode = true; }
            case NONE -> ensureOnlyStasisActive();
        }
    }

    private void setPassiveMode(PassiveMode m) {
        builderMode = (m == PassiveMode.BUILDER);
        gathererMode = (m == PassiveMode.GATHERER);
        stasisMode = (m == PassiveMode.STASIS);
        setPassiveMemory(m);
    }

    private void setPassiveMemory(PassiveMode m) { lastPassiveMode = m; }

    private void disablePassiveModes() {
        builderMode = false;
        gathererMode = false;
        stasisMode = false;
    }

    private boolean anyPassiveActive() {
        return builderMode || gathererMode || stasisMode;
    }

    private void ensureOnlyStasisActive() {
        combatMode = false;
        builderMode = false;
        gathererMode = false;
        stasisMode = true;
        setPassiveMemory(PassiveMode.STASIS);
        applyModuleFlags();
    }

    private void applyModuleFlags() {
        // Combat suite
        maceModule.setActive(combatMode);
        swordModule.setActive(combatMode);
        crystalModule.setActive(combatMode);
        cartModule.setActive(combatMode);
        // Passive suite
        builderModule.setActive(builderMode);
        gathererModule.setActive(gathererMode);
        stasisModule.setActive(stasisMode);
    }

    private void tickCombatSuite(ContextScanner.Signals s) {
        // Priority ordering: Crystal > Mace > Sword > Cart
        if (s.crystalOpportunity()) {
            crystalModule.tick(s);
            recordOutcome("CrystalModule", true);
            return;
        }
        if (s.isFlyingWithElytra() && s.hasMaceEquipped) {
            maceModule.tick(s);
            recordOutcome("MaceModule", true);
            return;
        }
        if (s.inCombat()) {
            swordModule.tick(s);
            recordOutcome("SwordModule", true);
            return;
        }
        cartModule.tick(s);
        recordOutcome("CartModule", true);
    }

    private void tickPassiveSuite(ContextScanner.Signals s) {
        if (builderMode) {
            builderModule.tick(s);
            recordOutcome("BuilderModule", true);
            return;
        }
        if (gathererMode) {
            gathererModule.tick(s);
            recordOutcome("GathererModule", true);
            return;
        }
        stasisModule.tick(s);
        recordOutcome("StasisModule", true);
    }

    // ---- Outcome reporting to AI subsystems ---------------------------------

    private void recordOutcome(String moduleName, boolean success) {
        try {
            if (trainingManager != null) {
                if (success) trainingManager.recordSuccess(moduleName);
                else trainingManager.recordFailure(moduleName);
            }
            if (memoryManager != null) {
                memoryManager.recordExperience(moduleName, scanner.getLastSignals(), success);
            }
            if (success && rewardSystem != null) {
                rewardSystem.reward(moduleName, 1);
            }
            if (!success && punishmentSystem != null) {
                punishmentSystem.punish(moduleName, 1);
            }
        } catch (Exception e) {
            LOGGER.debug("Outcome reporting error for {}: {}", moduleName, e.getMessage());
        }
    }
}
