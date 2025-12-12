package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.LearningStore;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ChatMonitor: stores chat messages and provides quick TL;DR summaries.
 */
public class ChatMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-chat");
    private final LearningStore store;

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
        } catch (Exception e) {
            LOGGER.debug("Failed to record chat: {}", e.getMessage());
        }
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
}
