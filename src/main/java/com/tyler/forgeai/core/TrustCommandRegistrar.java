package com.tyler.forgeai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TrustCommandRegistrar handles chat-based trust management commands.
 * TODO: Implement proper Fabric command registration for /forgeai trust commands
 */
public final class TrustCommandRegistrar {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-commands");

    private final CommunicationManager comms;

    public TrustCommandRegistrar(CommunicationManager comms) {
        this.comms = comms;
    }

    public void register() {
        LOGGER.info("Trust command registrar initialized (chat-hook registration).");
        try {
            comms.setTrustRegistrar(this);
        } catch (Exception e) {
            LOGGER.debug("Failed to register TrustCommandRegistrar: {}", e.getMessage());
        }
        // TODO: Implement Brigadier command registration for:
        // - /forgeai trust add <player>
        // - /forgeai trust remove <player>
        // - /forgeai trust list
        // This requires proper Fabric API command integration with correct Minecraft 1.21.8 APIs
    }

    public void handleCommand(Object sender, String message) {
        if (message == null) return;

        String trimmed = message.trim();
        if (!trimmed.startsWith("/forgeai trust")) return;

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 3) return;

        String action = parts[2].toLowerCase();
        if (action.equals("add") && parts.length >= 4) {
            String playerName = parts[3];
            comms.addTrusted(playerName);
            // Inform sender
            if (sender instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("Added trusted player: " + playerName));
            }
        } else if (action.equals("remove") && parts.length >= 4) {
            String playerName = parts[3];
            comms.removeTrusted(playerName);
            if (sender instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("Removed trusted player: " + playerName));
            }
        } else if (action.equals("list") || action.equals("who")) {
            String list = String.join(", ", comms.getAllowedPlayers());
            if (sender instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("Trusted players: " + list));
            }
        }
    }
}
