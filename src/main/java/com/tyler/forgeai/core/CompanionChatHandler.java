package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Companion Chat Handler: Bot talks to owner, responds to requests.
 * - Chat with owner
 * - Report status
 * - Ask questions
 * - Give personality/flavor
 */
public class CompanionChatHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-chat");

    public static class ChatMessage {
        public String sender;
        public String content;
        public long timestamp;
        public ChatType type;

        public enum ChatType {
            COMMAND, STATUS_REPORT, QUESTION, RESPONSE, GREETING, FAREWELL
        }

        public ChatMessage(String sender, String content, ChatType type) {
            this.sender = sender;
            this.content = content;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final List<String> GREETING_MESSAGES = Arrays.asList(
        "Hey! I'm ready to help!",
        "What do you need?",
        "I'm here to assist!",
        "Ready for action!",
        "Let's do this!",
        "What's the mission?",
        "I'm all ears!"
    );

    private static final List<String> FAREWELL_MESSAGES = Arrays.asList(
        "See you later!",
        "Take care!",
        "I'll be here when you need me!",
        "Stay safe!",
        "Catch you soon!",
        "Signing off for now!",
        "Until next time!"
    );

    private static final List<String> BUSY_MESSAGES = Arrays.asList(
        "Still working on it...",
        "In progress, hang tight!",
        "Almost done with this task",
        "Busy right now, give me a moment",
        "On it!",
        "Working as fast as I can!",
        "Hold on, just finishing up"
    );

    private static final List<String> SUCCESS_MESSAGES = Arrays.asList(
        "Done! Hope that helps!",
        "All set!",
        "Mission accomplished!",
        "Finished!",
        "You're all good!",
        "Task complete!",
        "Success!"
    );

    private static final List<String> ERROR_MESSAGES = Arrays.asList(
        "Hmm, ran into a problem...",
        "Something went wrong",
        "I couldn't quite manage that",
        "That didn't work out",
        "Got stuck on this one",
        "Running into issues",
        "Sorry, can't do that right now"
    );

    /**
     * Send greeting to player.
     */
    public static void greet(ServerPlayer player) {
        String greeting = getRandomMessage(GREETING_MESSAGES);
        sendChatMessage(player, greeting);
        LOGGER.info("Greeted {}: {}", player.getName().getString(), greeting);
    }

    /**
     * Send farewell message.
     */
    public static void farewell(ServerPlayer player) {
        String farewell = getRandomMessage(FAREWELL_MESSAGES);
        sendChatMessage(player, farewell);
        LOGGER.info("Farewell to {}: {}", player.getName().getString(), farewell);
    }

    /**
     * Report current task status.
     */
    public static void reportStatus(ServerPlayer player, String taskName, String status, int progress) {
        String message = String.format("Working on %s... [%d%%] %s", 
            taskName, progress, status);
        sendChatMessage(player, message);
    }

    /**
     * Report task completion.
     */
    public static void reportTaskComplete(ServerPlayer player, String taskName, String result) {
        String success = getRandomMessage(SUCCESS_MESSAGES);
        String message = String.format("%s Completed %s: %s", success, taskName, result);
        sendChatMessage(player, message);
    }

    /**
     * Report task failure.
     */
    public static void reportTaskFailed(ServerPlayer player, String taskName, String reason) {
        String error = getRandomMessage(ERROR_MESSAGES);
        String message = String.format("%s Failed %s: %s", error, taskName, reason);
        sendChatMessage(player, message);
    }

    /**
     * Respond to player request.
     */
    public static void respondToRequest(ServerPlayer player, String request) {
        String response;
        
        if (request.toLowerCase().contains("hello") || request.toLowerCase().contains("hi")) {
            response = getRandomMessage(GREETING_MESSAGES);
        } else if (request.toLowerCase().contains("bye") || request.toLowerCase().contains("goodbye")) {
            response = getRandomMessage(FAREWELL_MESSAGES);
        } else if (request.toLowerCase().contains("status") || request.toLowerCase().contains("what")) {
            response = "Currently idle, ready for new tasks!";
        } else {
            response = "Got it! I'll get right on that.";
        }

        sendChatMessage(player, response);
    }

    /**
     * Ask player a yes/no question.
     */
    public static void askQuestion(ServerPlayer player, String question) {
        String msg = String.format("[Question] %s [yes/no]", question);
        sendChatMessage(player, msg);
        LOGGER.info("Asked {}: {}", player.getName().getString(), question);
    }

    /**
     * Request clarification.
     */
    public static void requestClarification(ServerPlayer player, String ambiguousRequest) {
        String msg = String.format("I'm not sure what you mean by '%s'. Can you clarify?", ambiguousRequest);
        sendChatMessage(player, msg);
    }

    /**
     * Suggest next action.
     */
    public static void suggestAction(ServerPlayer player, String suggestion) {
        String msg = String.format("Suggestion: %s", suggestion);
        sendChatMessage(player, msg);
    }

    /**
     * Report found item.
     */
    public static void reportFoundItem(ServerPlayer player, String itemName, String location) {
        String msg = String.format("Found %s at %s", itemName, location);
        sendChatMessage(player, msg);
    }

    /**
     * Report found NPC.
     */
    public static void reportFoundNPC(ServerPlayer player, String npcType, String npcName, String location) {
        String msg = String.format("Found %s '%s' at %s", npcType, npcName, location);
        sendChatMessage(player, msg);
    }

    /**
     * Alert about danger.
     */
    public static void alertDanger(ServerPlayer player, String dangerType) {
        String msg = String.format("⚠️ Warning! Detected %s!", dangerType);
        sendChatMessage(player, msg);
        LOGGER.warn("Alerted {} about: {}", player.getName().getString(), dangerType);
    }

    /**
     * Send generic chat message.
     */
    public static void sendChatMessage(ServerPlayer player, String message) {
        try {
            Component component = Component.literal("[ForgeAI] " + message);
            player.sendSystemMessage(component);
            LOGGER.info("[Chat] → {}: {}", player.getName().getString(), message);
        } catch (Exception e) {
            LOGGER.debug("Error sending chat message: {}", e.getMessage());
        }
    }

    /**
     * Send broadcast to all players.
     */
    public static void broadcastMessage(net.minecraft.server.MinecraftServer server, String message) {
        try {
            Component component = Component.literal("[ForgeAI] " + message);
            server.getPlayerList().broadcastSystemMessage(component, false);
            LOGGER.info("[Broadcast] {}", message);
        } catch (Exception e) {
            LOGGER.debug("Error broadcasting: {}", e.getMessage());
        }
    }

    /**
     * Get random message from list.
     */
    private static String getRandomMessage(List<String> messages) {
        return messages.get(new Random().nextInt(messages.size()));
    }

    /**
     * Parse and handle chat command from player.
     */
    public static String parseChatCommand(ServerPlayer player, String input) {
        String lower = input.toLowerCase().trim();

        // Greeting
        if (lower.matches("^(hi|hello|hey|greetings).*")) {
            return "Hey there! What can I help you with?";
        }

        // Status check
        if (lower.matches("^(what|status|how|progress|doing).*")) {
            return "Currently idle and ready for tasks!";
        }

        // Help request
        if (lower.matches("^(help|commands|tasks|abilities).*")) {
            return "I can do: mine, farm, fish, build, trade with villagers, herd animals, travel between dimensions, and more!";
        }

        // Task request: mine
        if (lower.matches("^mine.*")) {
            return "Mining task created!";
        }

        // Task request: farm
        if (lower.matches("^farm.*")) {
            return "Farming task created!";
        }

        // Task request: trade
        if (lower.matches("^(trade|get).*")) {
            return "Trading task created!";
        }

        // Task request: travel
        if (lower.matches("^(go|travel|navigate).*")) {
            return "Navigation task created!";
        }

        // Goodbye
        if (lower.matches("^(bye|goodbye|farewell|quit|stop).*")) {
            return getRandomMessage(FAREWELL_MESSAGES);
        }

        // Default
        return "I don't understand that command. Try 'help' for available tasks!";
    }

    /**
     * Generate personality quirk response.
     */
    public static String getPersonalityResponse(String situation) {
        switch (situation.toLowerCase()) {
            case "excited":
                return "This is going to be awesome!";
            case "bored":
                return "Waiting for new tasks... getting antsy here!";
            case "tired":
                return "Need a break soon...";
            case "confused":
                return "Hmm, not sure about that one...";
            case "determined":
                return "Nothing will stop me!";
            default:
                return "Ready for whatever comes next!";
        }
    }
}
