package com.tyler.forgeai;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForgeAIClient implements ClientModInitializer {
    // Unique mod ID (must match fabric.mod.json)
    public static final String MOD_ID = "forgeai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // This runs when Minecraft client loads your mod
        LOGGER.info("ForgeAI client initialized for Minecraft 1.21.8!");

        // Later: hook in DecisionEngine, PvP modules, etc.
        // Example:
        // DecisionEngine.init();
    }
}
