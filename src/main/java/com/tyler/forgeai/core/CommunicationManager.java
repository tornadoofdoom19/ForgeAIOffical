package com.tyler.forgeai.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
        private TrustCommandRegistrar trustRegistrar = null;
        private com.tyler.forgeai.core.ChatMonitor chatMonitor = null;
    private final Gson gson = new Gson();

    public void init() {
        loadAllowedList();
        LOGGER.info("CommunicationManager initialized with " + allowedPlayers.size() + " trusted players.");
    }

    public Set<String> getAllowedPlayers() {
        return Set.copyOf(allowedPlayers);
    }

    public boolean isTrusted(Object player) {
        String name = normalize(getPlayerName(player));
        return allowedPlayers.contains(name) || isBotAccount(player);
    }

    private String getPlayerName(Object player) {
        // Extract player name from ServerPlayer or fallback to string representation
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            return sp.getGameProfile().getName();
        }
        if (player instanceof String s) {
            return s;
        }
        try {
            return player.toString().split("@")[0];
        } catch (Exception e) {
            return "unknown";
        }
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

    public void handleChatMessage(Object sender, String message) {
        // Check for trust command (admins only). Allow parsing even if not yet trusted
        if (message != null && message.startsWith("/forgeai trust")) {
            if (trustRegistrar != null) {
                trustRegistrar.handleCommand(sender, message);
                return;
            }
        }

        if (!isTrusted(sender)) {
            LOGGER.debug("Ignoring chat from untrusted player");
            return;
        }
        LOGGER.info("ForgeAI received chat: " + message);
        try { if (chatMonitor != null) chatMonitor.recordMessage(sender, message); } catch (Exception ignored) {}
        // Route to prompt parser for decision processing
        try {
            var decision = com.tyler.forgeai.api.PromptParser.parsePrompt(message);
            if (decision != null && sender instanceof net.minecraft.server.level.ServerPlayer sp) {
                LOGGER.info("Processing decision from chat: {}", decision);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse chat message: {}", e.getMessage());
        }
    }

    public void handlePrivateMessage(Object sender, String message, String channelType) {
        if (!isTrusted(sender)) {
            LOGGER.debug("Ignoring private message from untrusted player");
            return;
        }
        LOGGER.info("ForgeAI received private message (" + channelType + "): " + message);
        // Parse PM formats per channel type (Discord, Telegram, etc.)
        try {
            // Extract actual command from channel format
            String command = message;
            if ("discord".equalsIgnoreCase(channelType) && message.startsWith("!")) {
                command = message.substring(1);
            } else if ("telegram".equalsIgnoreCase(channelType) && message.startsWith("/")) {
                command = message.substring(1);
            }
            var decision = com.tyler.forgeai.api.PromptParser.parsePrompt(command);
            LOGGER.info("Processing PM command: {}", decision);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse private message: {}", e.getMessage());
        }
    }

    public void setTrustRegistrar(TrustCommandRegistrar registrar) {
        this.trustRegistrar = registrar;
        LOGGER.info("Trust registrar attached to CommunicationManager");
    }

    public void setChatMonitor(com.tyler.forgeai.core.ChatMonitor monitor) {
        this.chatMonitor = monitor;
        LOGGER.info("ChatMonitor attached to CommunicationManager");
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

    private boolean isBotAccount(Object player) {
        // Check if player name contains known bot indicators
        String name = getPlayerName(player);
        return name.toLowerCase().contains("bot") || name.toLowerCase().contains("forge_ai") || name.toLowerCase().contains("ai_");
    }
}
