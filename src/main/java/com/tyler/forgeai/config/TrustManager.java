package com.tyler.forgeai.config;

import com.tyler.forgeai.core.CommunicationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-trust");

    private final CommunicationManager comms;
    private final ConfigLoader configLoader;

    public TrustManager(CommunicationManager comms, ConfigLoader configLoader) {
        this.comms = comms;
        this.configLoader = configLoader;
    }

    public void init() {
        LOGGER.info("TrustManager initialized — enforcing trust policies.");
    }

    /**
     * Check if a player is trusted under current policies.
     */
    public boolean isTrusted(Object player) {
        // Always trust bot accounts (placeholder logic)
        if (isBotAccount(player)) return true;

        // Enforce allowed list
        boolean trusted = comms.isTrusted(player);

        // Enforce max trusted players policy
        if (trusted && comms.getAllowedPlayers().size() > configLoader.getConfig().maxTrustedPlayers) {
            LOGGER.warn("Trusted list exceeds max allowed (" + configLoader.getConfig().maxTrustedPlayers + ")");
            return false;
        }

        return trusted;
    }

    /**
     * Auto-trust detection for bot accounts running ForgeAI.
     */
    private boolean isBotAccount(Object player) {
        // TODO: Implement detection (e.g., tag, UUID, or config flag)
        return false;
    }

    /**
     * Handle server-specific private message rules.
     */
    public boolean validatePrivateMessageFormat(String channelType) {
        // Example: allow EssentialsX-style PMs, block unknown formats
        switch (channelType.toLowerCase()) {
            case "essentialsx":
            case "vanilla":
            case "towny":
                return true;
            default:
                LOGGER.debug("Blocked PM from unsupported channel type: " + channelType);
                return false;
        }
    }
}
