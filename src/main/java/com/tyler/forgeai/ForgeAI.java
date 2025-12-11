package com.tyler.forgeai;

import com.tyler.forgeai.ai.MemoryManager;
import com.tyler.forgeai.ai.PunishmentSystem;
import com.tyler.forgeai.ai.RewardSystem;
import com.tyler.forgeai.ai.TrainingManager;
import com.tyler.forgeai.core.CommunicationManager;
import com.tyler.forgeai.core.ContextScanner;
import com.tyler.forgeai.core.DecisionEngine;
import com.tyler.forgeai.core.TrustCommandRegistrar;
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

        // Register trust commands - TODO: Fix command API compatibility
        // TrustCommandRegistrar trustRegistrar = new TrustCommandRegistrar(comms);
        // trustRegistrar.register();

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
