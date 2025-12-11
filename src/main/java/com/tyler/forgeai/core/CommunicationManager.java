package com.tyler.forgeai.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

public class CommunicationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-comms");
    private static final String ALLOWED_FILE = "config/forgeai_allowed.json";

    private final Set<String> allowedPlayers = new HashSet<>();
    private final Gson gson = new Gson();

    public void init() {
        loadAllowedList();
        LOGGER.info("CommunicationManager initialized with " + allowedPlayers.size() + " trusted players.");
    }

    public Set<String> getAllowedPlayers() {
        return Set.copyOf(allowedPlayers);
    }

    public boolean isTrusted(ServerPlayerEntity player) {
        String name = normalize(player.getName().getString());
        return allowedPlayers.contains(name) || isBotAccount(player);
    }

    public void addTrusted(String playerName) {
        String n = normalize(playerName);
        if (n.isEmpty()) return;
        allowedPlayers.add(n);
        saveAllowedList();
        LOGGER.info("Added trusted player: " + n);
    }

    public void removeTrusted(String playerName) {
        String n = normalize(playerName);
        if (n.isEmpty()) return;
        allowedPlayers.remove(n);
        saveAllowedList();
        LOGGER.info("Removed trusted player: " + n);
    }

    public void handleChatMessage(ServerPlayerEntity sender, String message) {
        if (!isTrusted(sender)) {
            LOGGER.debug("Ignoring chat from untrusted player: " + sender.getName().getString());
            return;
        }
        LOGGER.info("ForgeAI received chat from " + sender.getName().getString() + ": " + message);
        // TODO: route to PromptParser or DecisionEngine
    }

    public void handlePrivateMessage(ServerPlayerEntity sender, String message, String channelType) {
        if (!isTrusted(sender)) {
            LOGGER.debug("Ignoring private message from untrusted player: " + sender.getName().getString());
            return;
        }
        LOGGER.info("ForgeAI received private message (" + channelType + ") from " + sender.getName().getString() + ": " + message);
        // TODO: parse PM formats per server type
    }

    private void loadAllowedList() {
        try (FileReader reader = new FileReader(ALLOWED_FILE)) {
            Type setType = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = gson.fromJson(reader, setType);
            if (loaded != null) allowedPlayers.addAll(loaded);
        } catch (Exception e) {
            LOGGER.warn("No existing allowed list found, starting fresh.");
        }
    }

    private void saveAllowedList() {
        try (FileWriter writer = new FileWriter(ALLOWED_FILE)) {
            gson.toJson(allowedPlayers, writer);
        } catch (Exception e) {
            LOGGER.error("Failed to save allowed list: ", e);
        }
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim();
    }

    private boolean isBotAccount(ServerPlayerEntity player) {
        // TODO: implement detection for accounts running ForgeAI itself
        return false;
    }
}
