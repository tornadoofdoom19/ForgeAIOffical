package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.tyler.forgeai.config.FriendsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * BotCommunicationManager: Sends messages to trusted players (friends) asking for decisions.
 * Used for runtime decisions like "should I pause task for sleep?" or "can I proceed with task?"
 */
public class BotCommunicationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-comms");

    private final String botName;
    private final FriendsList friendsList;
    private final Map<String, PendingQuestion> pendingQuestions = new HashMap<>();

    public static class PendingQuestion {
        public String questionId;
        public String askerBot;
        public String targetPlayer;
        public String question;
        public long createdAt;
        public long expiresAt;
        public String response;  // "yes", "no", "timeout"

        public PendingQuestion(String askerBot, String targetPlayer, String question) {
            this.questionId = UUID.randomUUID().toString().substring(0, 8);
            this.askerBot = askerBot;
            this.targetPlayer = targetPlayer;
            this.question = question;
            this.createdAt = System.currentTimeMillis();
            this.expiresAt = createdAt + 30000;  // 30 second timeout
            this.response = null;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }

        @Override
        public String toString() {
            return String.format("Q{%s: %s -> %s}", questionId, askerBot, question);
        }
    }

    public BotCommunicationManager(String botName, FriendsList friendsList) {
        this.botName = botName;
        this.friendsList = friendsList;
        LOGGER.info("BotCommunicationManager initialized for bot: {}", botName);
    }

    /**
     * Ask primary owner a yes/no question (e.g., "Pause for sleep?").
     * Returns immediately with question ID; caller should poll for response.
     */
    public String askOwnerYesNo(MinecraftServer server, String question) {
        String owner = friendsList.getPrimaryOwner();
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayerByName(owner);

        if (ownerPlayer == null) {
            LOGGER.warn("Primary owner {} is not online; cannot ask: {}", owner, question);
            return null;
        }

        if (!friendsList.canMessagePlayer(owner)) {
            LOGGER.debug("Rate limit active for player {}", owner);
            return null;
        }

        PendingQuestion q = new PendingQuestion(botName, owner, question);
        pendingQuestions.put(q.questionId, q);

        // Send message to player
        String msg = String.format("§b[%s]§r %s §7(Reply with: /botreply %s yes/no)§r", botName, question, q.questionId);
        ownerPlayer.sendSystemMessage(Component.literal(msg));

        LOGGER.info("Asked owner: {} (id={})", question, q.questionId);
        return q.questionId;
    }

    /**
     * Ask a specific friend a question.
     */
    public String askFriendYesNo(MinecraftServer server, String friendName, String question) {
        if (!friendsList.isTrusted(friendName)) {
            LOGGER.warn("Attempting to ask untrusted player: {}", friendName);
            return null;
        }

        ServerPlayer friendPlayer = server.getPlayerList().getPlayerByName(friendName);
        if (friendPlayer == null) {
            LOGGER.debug("Friend {} is not online", friendName);
            return null;
        }

        if (!friendsList.canMessagePlayer(friendName)) {
            LOGGER.debug("Rate limit active for player {}", friendName);
            return null;
        }

        PendingQuestion q = new PendingQuestion(botName, friendName, question);
        pendingQuestions.put(q.questionId, q);

        String msg = String.format("§b[%s]§r %s §7(Reply with: /botreply %s yes/no)§r", botName, question, q.questionId);
        friendPlayer.sendSystemMessage(Component.literal(msg));

        LOGGER.info("Asked friend {} : {} (id={})", friendName, question, q.questionId);
        return q.questionId;
    }

    /**
     * Record a response to a pending question.
     */
    public void recordResponse(String questionId, String response) {
        PendingQuestion q = pendingQuestions.get(questionId);
        if (q != null) {
            q.response = response.toLowerCase();
            LOGGER.info("Response recorded for {}: {}", questionId, response);
        }
    }

    /**
     * Check if question has a response (yes, no, or timeout).
     */
    public String getQuestionResponse(String questionId) {
        PendingQuestion q = pendingQuestions.get(questionId);
        if (q == null) return null;

        if (q.response != null) {
            return q.response;  // "yes" or "no"
        }

        if (q.isExpired()) {
            q.response = "timeout";
            return "timeout";
        }

        return null;  // Still waiting
    }

    /**
     * Notify friends about bot status (informational).
     */
    public void broadcastStatus(MinecraftServer server, String statusMessage) {
        ServerPlayer owner = server.getPlayerList().getPlayerByName(friendsList.getPrimaryOwner());
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("§b[" + botName + "]§r §7" + statusMessage + "§r"));
        }

        for (String friend : friendsList.getTrustedFriends()) {
            ServerPlayer friendPlayer = server.getPlayerList().getPlayerByName(friend);
            if (friendPlayer != null && friendsList.canMessagePlayer(friend)) {
                friendPlayer.sendSystemMessage(Component.literal("§b[" + botName + "]§r §7" + statusMessage + "§r"));
            }
        }
    }

    /**
     * Clean up expired questions.
     */
    public void cleanupExpiredQuestions() {
        Iterator<Map.Entry<String, PendingQuestion>> iter = pendingQuestions.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, PendingQuestion> entry = iter.next();
            if (entry.getValue().isExpired() && entry.getValue().response != null) {
                iter.remove();
            }
        }
    }

    /**
     * Get all pending questions for debugging.
     */
    public Collection<PendingQuestion> getPendingQuestions() {
        return new ArrayList<>(pendingQuestions.values());
    }
}
