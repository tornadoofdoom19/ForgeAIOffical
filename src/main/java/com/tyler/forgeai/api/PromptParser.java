package com.tyler.forgeai.api;

import com.tyler.forgeai.core.ContextScanner.Signals;
import com.tyler.forgeai.core.DecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PromptParser interprets player input (commands or natural language)
 * and routes it into structured actions for the DecisionEngine.
 */
public class PromptParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-prompt");

    private final DecisionEngine decisionEngine;

    public PromptParser(DecisionEngine decisionEngine) {
        this.decisionEngine = decisionEngine;
    }

    public void init() {
        LOGGER.info("PromptParser initialized.");
    }

    /**
     * Parse a raw prompt string and route to DecisionEngine.
     */
    public void parse(String prompt, Signals context) {
        String msg = prompt.trim().toLowerCase();

        // Explicit command parsing
        if (msg.startsWith("!combat on")) {
            decisionEngine.enableCombatMode(true);
            return;
        } else if (msg.startsWith("!combat off")) {
            decisionEngine.enableCombatMode(false);
            return;
        } else if (msg.startsWith("!builder on")) {
            decisionEngine.enableBuilderMode(true);
            decisionEngine.enableCombatMode(false);
            return;
        } else if (msg.startsWith("!gather on")) {
            decisionEngine.enableGathererMode(true);
            decisionEngine.enableCombatMode(false);
            return;
        } else if (msg.startsWith("!stasis on")) {
            decisionEngine.enableStasisMode(true);
            decisionEngine.enableCombatMode(false);
            return;
        }

        // Set build location: '!setbuild x y z'
        if (msg.startsWith("!setbuild ")) {
            String[] parts = msg.split("\\s+");
            if (parts.length >= 4) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);
                    int z = Integer.parseInt(parts[3]);
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (decisionEngine.getTaskManager() != null) {
                        decisionEngine.getTaskManager().setBuildLocation(pos);
                        decisionEngine.enableBuilderMode(true);
                        if (context != null && context.player != null) {
                            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(context.player, "Build location set to " + pos.toShortString());
                        }
                    }
                } catch (Exception ignored) {}
            }
            return;
        }

        // Natural language interpretation (basic scaffolding)
        if (msg.contains("fight") || msg.contains("attack")) {
            decisionEngine.enableCombatMode(true);
        } else if (msg.contains("build")) {
            decisionEngine.enableBuilderMode(true);
        } else if (msg.contains("mine") || msg.contains("gather")) {
            decisionEngine.enableGathererMode(true);
        } else if (msg.contains("rest") || msg.contains("idle")) {
            decisionEngine.enableStasisMode(true);
        } else {
            LOGGER.debug("Unhandled prompt — no direct mode change: {}", prompt);
        }
    }

    /**
     * Convenience static parser for quick command extraction from chat messages.
     */
    public static com.tyler.forgeai.core.AICommandParser.ParsedCommand parsePrompt(String message) {
        try {
            return com.tyler.forgeai.core.AICommandParser.parse(message);
        } catch (Exception e) {
            LOGGER.debug("parsePrompt failed: {}", e.getMessage());
            return null;
        }
    }
}
