package com.tyler.forgeai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * AI Command Parser: Parse and execute user commands for multi-purpose AI.
 * Supports:
 * - PvP practice commands (attack player, defend, etc.)
 * - Task commands (farm wheat, mine gold, afk iron farm, etc.)
 * - Chat commands (say, whisper, private message)
 * - Navigation commands (go to, teleport, waypoint)
 * - Reinforcement learning feedback (good, bad, reward, punish)
 */
public class AICommandParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-command");

    public enum CommandType {
        // PvP Commands
        PVP_ATTACK_PLAYER("attack|fight|combat", CommandCategory.PVP),
        PVP_DEFEND("defend|protect", CommandCategory.PVP),
        PVP_FLEE("flee|run|escape", CommandCategory.PVP),
        
        // Task Commands
        TASK_FARM_WHEAT("farm wheat|harvest wheat", CommandCategory.TASK),
        TASK_FARM_CARROTS("farm carrots|harvest carrots", CommandCategory.TASK),
        TASK_MINE_GOLD("mine gold|farm gold", CommandCategory.TASK),
        TASK_MINE_IRON("mine iron|farm iron|afk iron", CommandCategory.TASK),
        TASK_RAID_FARM("raid farm|raid|collect raids", CommandCategory.TASK),
        TASK_BOTTLE_FARM("bottle farm|exp farm|bottle xp", CommandCategory.TASK),
        TASK_AFK("afk|idle|sit", CommandCategory.TASK),
        
        // Chat Commands
        CHAT_SAY_PUBLIC("say|chat|public", CommandCategory.CHAT),
        CHAT_WHISPER_PRIVATE("whisper|private|dm|message", CommandCategory.CHAT),
        CHAT_MESSAGE_PLAYER("tell|msg", CommandCategory.CHAT),
        
        // Navigation Commands
        NAV_GO_TO("go to|navigate|move to", CommandCategory.NAV),
        NAV_TELEPORT("teleport|tp", CommandCategory.NAV),
        NAV_HOME("home|base|spawn", CommandCategory.NAV),
        
        // RL Feedback Commands
        RL_REWARD("good|reward|excellent|well done", CommandCategory.RL_FEEDBACK),
        RL_PUNISH("bad|punish|wrong|mistake", CommandCategory.RL_FEEDBACK),
        RL_RESET("reset|restart|clear", CommandCategory.RL_FEEDBACK);

        private final String pattern;
        private final CommandCategory category;

        CommandType(String pattern, CommandCategory category) {
            this.pattern = pattern;
            this.category = category;
        }

        public String getPattern() { return pattern; }
        public CommandCategory getCategory() { return category; }
    }

    public enum CommandCategory {
        PVP, TASK, CHAT, NAV, RL_FEEDBACK
    }

    public static class ParsedCommand {
        public final CommandType type;
        public final CommandCategory category;
        public final String rawInput;
        public final Map<String, String> parameters;
        public final long timestamp;

        public ParsedCommand(CommandType type, String rawInput, Map<String, String> parameters) {
            this.type = type;
            this.category = type.getCategory();
            this.rawInput = rawInput;
            this.parameters = new HashMap<>(parameters);
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s", category, type, rawInput);
        }
    }

    /**
     * Parse user input string into AI command.
     */
    public static ParsedCommand parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        String lowerInput = input.toLowerCase().trim();
        Map<String, String> params = new HashMap<>();

        // Extract parameters before pattern matching
        String[] parts = lowerInput.split("\\s+", 2);
        String command = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        // Try to match command type
        for (CommandType type : CommandType.values()) {
            if (lowerInput.matches(".*\\b(" + type.getPattern() + ")\\b.*")) {
                // Extract relevant parameters based on command type
                extractParameters(type, args, params);
                
                LOGGER.info("Parsed command: {} -> {}", input, type);
                return new ParsedCommand(type, input, params);
            }
        }

        LOGGER.warn("Could not parse command: {}", input);
        return null;
    }

    /**
     * Extract parameters specific to command type.
     */
    private static void extractParameters(CommandType type, String args, Map<String, String> params) {
        switch (type.getCategory()) {
            case PVP:
                // Extract target player name if provided
                if (!args.isEmpty()) {
                    params.put("target", args);
                }
                break;

            case TASK:
                // Extract task parameters
                params.put("duration", extractDuration(args));
                params.put("position", extractPosition(args));
                break;

            case CHAT:
                if (type == CommandType.CHAT_WHISPER_PRIVATE) {
                    params.put("channel", "private");
                } else if (type == CommandType.CHAT_SAY_PUBLIC) {
                    params.put("channel", "public");
                }
                params.put("message", args);
                break;

            case NAV:
                params.put("destination", args);
                break;

            case RL_FEEDBACK:
                params.put("action", type.name());
                break;
        }
    }

    /**
     * Extract duration from arguments (e.g., "1 hour", "30 minutes").
     */
    private static String extractDuration(String args) {
        if (args.contains("hour")) {
            return "1h";
        } else if (args.contains("minute")) {
            return "30m";
        }
        return "unlimited";
    }

    /**
     * Extract position from arguments.
     */
    private static String extractPosition(String args) {
        if (args == null || args.isEmpty()) return "";
        args = args.trim();
        // Try to parse numeric coordinates "x y z"
        String[] parts = args.split("\\s+");
        if (parts.length >= 3) {
            try {
                Double.parseDouble(parts[0]);
                Double.parseDouble(parts[1]);
                Double.parseDouble(parts[2]);
                return parts[0] + " " + parts[1] + " " + parts[2];
            } catch (NumberFormatException ignored) {}
        }

        // Named location convenience handling
        String lower = args.toLowerCase();
        if (lower.contains("home") || lower.contains("base") || lower.contains("spawn")) return "home";
        if (lower.contains("bed") || lower.contains("sleep")) return "bed";
        if (lower.contains("nether")) return "nether";
        if (lower.contains("end")) return "end";

        return args; // fallback raw position / name
    }

    /**
     * Get human-readable description of command.
     */
    public static String describe(ParsedCommand cmd) {
        if (cmd == null) return "Unknown command";

        switch (cmd.type) {
            case PVP_ATTACK_PLAYER:
                return String.format("Attack PvP opponent%s",
                    cmd.parameters.containsKey("target") ? " (" + cmd.parameters.get("target") + ")" : "");
            case PVP_DEFEND:
                return "Defend against attack";
            case PVP_FLEE:
                return "Flee from combat";

            case TASK_FARM_WHEAT:
                return "Farm wheat until completion";
            case TASK_FARM_CARROTS:
                return "Farm carrots until completion";
            case TASK_MINE_GOLD:
                return "Mine gold for resources";
            case TASK_MINE_IRON:
                return "AFK at iron farm";
            case TASK_RAID_FARM:
                return "Raid farm for resources";
            case TASK_BOTTLE_FARM:
                return "Bottle exp at farm";
            case TASK_AFK:
                return "AFK idle at current position";

            case CHAT_SAY_PUBLIC:
                return "Say message in public chat";
            case CHAT_WHISPER_PRIVATE:
                return "Send private message";
            case CHAT_MESSAGE_PLAYER:
                return "Message specific player";

            case NAV_GO_TO:
                return "Navigate to: " + cmd.parameters.get("destination");
            case NAV_TELEPORT:
                return "Teleport to: " + cmd.parameters.get("destination");
            case NAV_HOME:
                return "Return to home/base";

            case RL_REWARD:
                return "Reinforce positive behavior";
            case RL_PUNISH:
                return "Punish negative behavior";
            case RL_RESET:
                return "Reset AI state";

            default:
                return cmd.type.name();
        }
    }
}
