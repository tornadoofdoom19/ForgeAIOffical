package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.LearningStore;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * ChatMonitor: stores chat messages and provides quick TL;DR summaries.
 * Enhanced for conversation learning and response generation.
 */
public class ChatMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-chat");
    private final LearningStore store;
    private final Map<String, List<String>> conversationHistory = new HashMap<>();
    private final Map<String, String> personalityProfiles = new HashMap<>();

    public ChatMonitor(LearningStore store) {
        this.store = store;
    }

    public void recordMessage(Object sender, String message) {
        try {
            Map<String, Object> m = new HashMap<>();
            String name = sender instanceof ServerPlayer sp ? sp.getGameProfile().getName() : sender.toString();
            m.put("sender", name);
            m.put("message", message);
            m.put("timestamp", System.currentTimeMillis());
            store.record("chat", m);

            // Update conversation history
            conversationHistory.computeIfAbsent(name, k -> new ArrayList<>()).add(message);
            // Keep only last 50 messages per person
            List<String> history = conversationHistory.get(name);
            if (history.size() > 50) {
                history.remove(0);
            }

            // Analyze personality
            analyzePersonality(name, message);

        } catch (Exception e) {
            LOGGER.debug("Failed to record chat: {}", e.getMessage());
        }
    }

    /**
     * Analyze player personality from messages.
     */
    private void analyzePersonality(String playerName, String message) {
        String lower = message.toLowerCase();
        String personality = personalityProfiles.getOrDefault(playerName, "neutral");

        if (lower.contains("lol") || lower.contains("haha") || lower.contains("xd")) {
            personality = "humorous";
        } else if (lower.contains("wtf") || lower.contains("omg") || lower.contains("!")) {
            personality = "excitable";
        } else if (lower.contains("thanks") || lower.contains("please")) {
            personality = "polite";
        } else if (lower.contains("noob") || lower.contains("ez")) {
            personality = "competitive";
        }

        personalityProfiles.put(playerName, personality);
    }

    public String summarizeRecent(int n) {
        try {
            List<Map<String, Object>> msgs = store.getObservations("chat");
            if (msgs.isEmpty()) return "No recent chat.";
            return msgs.stream().skip(Math.max(0, msgs.size() - n))
                .map(m -> m.getOrDefault("sender", "unknown") + ": " + m.getOrDefault("message", ""))
                .collect(Collectors.joining(" | "));
        } catch (Exception e) {
            LOGGER.debug("Chat summary error: {}", e.getMessage());
            return "Error summarizing chat.";
        }
    }

    /**
     * Generate a contextual response based on learned conversation patterns.
     */
    public String generateResponse(String playerName, String context) {
        List<String> history = conversationHistory.get(playerName);
        String personality = personalityProfiles.getOrDefault(playerName, "neutral");

        if (history == null || history.isEmpty()) {
            return "Hello! How can I help you?";
        }

        // Simple response generation based on personality
        switch (personality) {
            case "humorous":
                return "Haha, that's funny! What's up?";
            case "excitable":
                return "OMG! Tell me more!";
            case "polite":
                return "Hello! How may I assist you?";
            case "competitive":
                return "Let's see what you've got!";
            default:
                return "Hi there! What would you like to do?";
        }
    }

    /**
     * Get notable events from recent conversations.
     */
    public String getNotableEvents() {
        try {
            List<Map<String, Object>> msgs = store.getObservations("chat");
            List<String> notable = new ArrayList<>();

            for (Map<String, Object> msg : msgs) {
                String message = (String) msg.getOrDefault("message", "");
                String lower = message.toLowerCase();
                if (lower.contains("found") || lower.contains("killed") || lower.contains("built") ||
                    lower.contains("discovered") || lower.contains("completed")) {
                    notable.add(msg.getOrDefault("sender", "unknown") + ": " + message);
                }
            }

            if (notable.isEmpty()) return "No notable events recently.";
            return "Notable events:\n" + String.join("\n", notable);

        } catch (Exception e) {
            LOGGER.debug("Notable events error: {}", e.getMessage());
            return "Error retrieving notable events.";
        }
    }

    /**
     * Get conversation highlights for a specific day.
     */
    public String getHighlightsForDay(long dayTimestamp) {
        // This would filter messages by day
        // For now, return recent highlights
        return "Recent highlights: " + summarizeRecent(10);
    }
}
