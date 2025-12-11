package com.tyler.forgeai.core;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForgeAI implements ModInitializer {
    // Unique mod ID (must match fabric.mod.json)
    public static final String MOD_ID = "forgeai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // This runs when Minecraft loads your mod
        LOGGER.info("ForgeAI initialized for Minecraft 1.21.8!");

        // Later: hook in DecisionEngine, PvP modules, etc.
        // Example:
        // DecisionEngine.init();
    }
}
