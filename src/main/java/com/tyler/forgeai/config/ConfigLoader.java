package com.tyler.forgeai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-config");
    private static final Path CONFIG_PATH = Path.of("config/forgeai_config.json");

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ForgeAIConfig config;

    public void init() {
        if (Files.exists(CONFIG_PATH)) {
            load();
        } else {
            config = new ForgeAIConfig(); // defaults
            save();
        }
        LOGGER.info("ForgeAI configuration loaded.");
    }

    public ForgeAIConfig getConfig() {
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            gson.toJson(config, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save ForgeAI config: ", e);
        }
    }

    private void load() {
        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            config = gson.fromJson(reader, ForgeAIConfig.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load ForgeAI config, using defaults.", e);
            config = new ForgeAIConfig();
        }
    }

    // Inner class representing config schema
    public static class ForgeAIConfig {
        public boolean combatEnabled = true;
        public boolean builderEnabled = false;
        public boolean gathererEnabled = false;
        public boolean stasisEnabled = false;

        public String logLevel = "INFO";
        public int maxTrustedPlayers = 50;
    }
}
