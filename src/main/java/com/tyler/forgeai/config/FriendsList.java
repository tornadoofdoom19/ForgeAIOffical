package com.tyler.forgeai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * FriendsList: Manages trusted players the bot listens to for commands.
 * The primary owner has full control; friends can give commands but with restrictions.
 */
public class FriendsList {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-friends");

    private String primaryOwner;                      // Main player (full control, can lock/unlock)
    private final Set<String> trustedFriends = new HashSet<>();  // Can give commands
    private final Map<String, Long> messageTimestamps = new HashMap<>();  // Rate limiting

    private static final long MESSAGE_COOLDOWN_MS = 1000;  // Min 1 sec between messages to same player

    public FriendsList(String primaryOwner) {
        this.primaryOwner = primaryOwner;
        LOGGER.info("FriendsList initialized with primary owner: {}", primaryOwner);
    }

    /**
     * Add a trusted friend.
     */
    public void addFriend(String playerName) {
        if (playerName.equalsIgnoreCase(primaryOwner)) {
            LOGGER.warn("Cannot add primary owner {} as friend", primaryOwner);
            return;
        }
        trustedFriends.add(playerName);
        LOGGER.info("Added friend: {}", playerName);
    }

    /**
     * Remove a trusted friend.
     */
    public void removeFriend(String playerName) {
        trustedFriends.remove(playerName);
        LOGGER.info("Removed friend: {}", playerName);
    }

    /**
     * Check if a player is trusted (owner or friend).
     */
    public boolean isTrusted(String playerName) {
        return playerName.equalsIgnoreCase(primaryOwner) || trustedFriends.contains(playerName);
    }

    /**
     * Check if player is the primary owner.
     */
    public boolean isPrimaryOwner(String playerName) {
        return playerName.equalsIgnoreCase(primaryOwner);
    }

    /**
     * Get primary owner.
     */
    public String getPrimaryOwner() {
        return primaryOwner;
    }

    /**
     * Set primary owner (only callable by current owner).
     */
    public void setPrimaryOwner(String newOwner, String callerName) {
        if (!isPrimaryOwner(callerName)) {
            LOGGER.warn("Non-owner {} attempted to change primary owner", callerName);
            return;
        }
        this.primaryOwner = newOwner;
        LOGGER.info("Primary owner changed to: {}", newOwner);
    }

    /**
     * Get all trusted friends (excludes primary owner).
     */
    public Set<String> getTrustedFriends() {
        return new HashSet<>(trustedFriends);
    }

    /**
     * Rate-limited message tracking (prevent spam to same player).
     */
    public boolean canMessagePlayer(String playerName) {
        long now = System.currentTimeMillis();
        Long lastMessage = messageTimestamps.get(playerName);

        if (lastMessage == null || (now - lastMessage) >= MESSAGE_COOLDOWN_MS) {
            messageTimestamps.put(playerName, now);
            return true;
        }
        return false;
    }
}
