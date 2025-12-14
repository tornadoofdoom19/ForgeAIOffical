package com.tyler.forgeai;

import com.tyler.forgeai.ai.MemoryManager;
import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.*;
import com.tyler.forgeai.ai.LearningStore;
import com.tyler.forgeai.modules.movement.MovementManager;
import com.tyler.forgeai.modules.utility.InventoryManager;
import com.tyler.forgeai.ai.CombatLearning;
import com.tyler.forgeai.core.ObservationManager;
import com.tyler.forgeai.core.ChatMonitor;
import com.tyler.forgeai.core.TaskLockManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForgeAI implements ModInitializer {
    public static final String MOD_ID = "forgeai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CommunicationManager comms;
    private static ContextScanner scanner;
    private static DecisionEngine decisionEngine;

    // AI subsystems
    private static TrainingManager trainingManager;
    private static MemoryManager memoryManager;
    private static RewardSystem rewardSystem;
    private static PunishmentSystem punishmentSystem;

    @Override
    public void onInitialize() {
        LOGGER.info("ForgeAI booting — Minecraft 1.21.8");

        // Core subsystems
        comms = new CommunicationManager();
        comms.init();

        scanner = new ContextScanner();
        scanner.init();

        decisionEngine = new DecisionEngine(scanner, comms);

        // Create task manager with decision engine as executor
        TaskManager taskManager = new TaskManager(decisionEngine);
        decisionEngine.setTaskManager(taskManager);

        // AI subsystems
        trainingManager = new TrainingManager();
        memoryManager = new MemoryManager();
        rewardSystem = new RewardSystem();
        punishmentSystem = new PunishmentSystem();

        trainingManager.init();
        memoryManager.init();
        rewardSystem.init();
        punishmentSystem.init();

        // Wire AI subsystems into the decision engine
        decisionEngine.setTrainingManager(trainingManager);
        decisionEngine.setMemoryManager(memoryManager);
        decisionEngine.setRewardSystem(rewardSystem);
        decisionEngine.setPunishmentSystem(punishmentSystem);

        decisionEngine.init();

        // Learning, movement and observation subsystems
        LearningStore learningStore = new LearningStore();
        MovementManager movementManager = new MovementManager();
        ObservationManager observationManager = new ObservationManager(learningStore);
        ChatMonitor chatMonitor = new ChatMonitor(learningStore);
        TaskLockManager taskLockManager = new TaskLockManager();
        InventoryManager inventoryManager = new InventoryManager();
        inventoryManager.attachLearningStore(learningStore);
        inventoryManager.init();
        // Combat learning - also records via CombatEventHandler
        CombatLearning combatLearning = new CombatLearning(learningStore);

        // Wire learning modules into decision engine
        decisionEngine.setLearningStore(learningStore);
        decisionEngine.setMovementManager(movementManager);
        decisionEngine.setObservationManager(observationManager);
        decisionEngine.setChatMonitor(chatMonitor);
        decisionEngine.setTaskLockManager(taskLockManager);

        // Wire RL feedback to event system
        CombatEventHandler.setGlobalRewardSystem(rewardSystem);
        CombatEventHandler.setGlobalPunishmentSystem(punishmentSystem);
        CombatEventHandler.setGlobalTrainingManager(trainingManager);
        LOGGER.info("RL feedback system wired to event handlers");

        // Register Minecraft event hooks for RL learning
        EventHookRegistry.registerCombatEventHooks();
        EventHookRegistry.registerNavigationEventHooks();
        EventHookRegistry.registerGatheringEventHooks();
        LOGGER.info("Combat event hooks registered — RL feedback active");

        // Register trust commands (via chat hook)
        TrustCommandRegistrar trustRegistrar = new TrustCommandRegistrar(comms);
        trustRegistrar.register();
        // Wire chat monitor into comms
        try { coms.setChatMonitor(chatMonitor); } catch (Exception ignored) {}        // Wire decision engine into comms for command execution
        comms.setDecisionEngine(decisionEngine);
        // Register tick loop
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                decisionEngine.tick(server);
            } catch (Exception e) {
                LOGGER.error("ForgeAI tick error: ", e);
            }
        });

        LOGGER.info("ForgeAI online — decision loop, AI subsystems, and trust commands active.");
    }

    public static CommunicationManager getComms() {
        return comms;
    }

    public static ContextScanner getScanner() {
        return scanner;
    }

    public static DecisionEngine getDecisionEngine() {
        return decisionEngine;
    }

    public static TrainingManager getTrainingManager() {
        return trainingManager;
    }

    public static MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public static RewardSystem getRewardSystem() {
        return rewardSystem;
    }

    public static PunishmentSystem getPunishmentSystem() {
        return punishmentSystem;
    }
}
