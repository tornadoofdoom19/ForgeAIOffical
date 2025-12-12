package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * BotRegistry: Central registry for all AI bots in a world.
 * Tracks active bots, allows inter-bot task delegation and subtask assignment.
 */
public class BotRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-registry");

    private static final Map<ServerLevel, BotRegistry> registries = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<String, BotInstance> activeBots = new HashMap<>();

    public static class BotInstance {
        public String botName;
        public ServerPlayer player;
        public DecisionEngine decisionEngine;
        public TaskManager taskManager;
        public String owner;  // Primary owner player name
        public long registeredAt;
        public boolean active;

        public BotInstance(String botName, ServerPlayer player, DecisionEngine engine, 
                          TaskManager tm, String owner) {
            this.botName = botName;
            this.player = player;
            this.decisionEngine = engine;
            this.taskManager = tm;
            this.owner = owner;
            this.registeredAt = System.currentTimeMillis();
            this.active = true;
        }

        @Override
        public String toString() {
            return String.format("Bot{%s, owner=%s, active=%s}", botName, owner, active);
        }
    }

    public BotRegistry(ServerLevel level) {
        this.level = level;
        LOGGER.info("BotRegistry created for level: {}", level.getWorld().getLevelName());
    }

    /**
     * Get or create registry for a world.
     */
    public static BotRegistry getOrCreateRegistry(ServerLevel level) {
        return registries.computeIfAbsent(level, BotRegistry::new);
    }

    /**
     * Register a bot in this world.
     */
    public void registerBot(String botName, ServerPlayer player, DecisionEngine engine,
                           TaskManager taskManager, String owner) {
        BotInstance instance = new BotInstance(botName, player, engine, taskManager, owner);
        activeBots.put(botName.toLowerCase(), instance);
        LOGGER.info("Registered bot: {} (owner: {})", botName, owner);
    }

    /**
     * Unregister a bot (when it goes offline or is removed).
     */
    public void unregisterBot(String botName) {
        BotInstance removed = activeBots.remove(botName.toLowerCase());
        if (removed != null) {
            LOGGER.info("Unregistered bot: {}", botName);
        }
    }

    /**
     * Get a bot by name.
     */
    public BotInstance getBot(String botName) {
        return activeBots.get(botName.toLowerCase());
    }

    /**
     * Get all active bots.
     */
    public Collection<BotInstance> getAllBots() {
        return new ArrayList<>(activeBots.values());
    }

    /**
     * Get all bots owned by a player.
     */
    public List<BotInstance> getBotsByOwner(String owner) {
        List<BotInstance> result = new ArrayList<>();
        for (BotInstance bot : activeBots.values()) {
            if (bot.owner.equalsIgnoreCase(owner)) {
                result.add(bot);
            }
        }
        return result;
    }

    /**
     * Assign a subtask to another bot (delegation).
     * Returns true if subtask was successfully queued.
     */
    public boolean delegateSubtask(String fromBotName, String toBot, TaskManager.Task subtask) {
        BotInstance targetBot = getBot(toBot);
        if (targetBot == null || !targetBot.active) {
            LOGGER.warn("Cannot delegate to bot {}: not found or inactive", toBot);
            return false;
        }

        targetBot.taskManager.enqueueTask(subtask);
        LOGGER.info("Delegated subtask from {} to {}: {}", fromBotName, toBot, subtask);
        return true;
    }

    /**
     * Find bots available for a specific role (miner, builder, gatherer, guard).
     */
    public List<BotInstance> findAvailableBotsForRole(String owner, String role) {
        List<BotInstance> result = new ArrayList<>();
        for (BotInstance bot : activeBots.values()) {
            if (!bot.owner.equalsIgnoreCase(owner) || !bot.active) continue;

            TaskManager.Task current = bot.taskManager.getCurrentTask();
            if (current == null || current.status == TaskManager.TaskStatus.QUEUED) {
                // Bot has no task or is idle; available
                result.add(bot);
            }
        }
        return result;
    }

    /**
     * Broadcast a message to all bots owned by a player.
     */
    public void broadcastToOwnerBots(String owner, String message) {
        for (BotInstance bot : getBotsByOwner(owner)) {
            LOGGER.info("[{}] Message: {}", bot.botName, message);
        }
    }

    /**
     * Check if a bot is still valid (player online, active).
     */
    public boolean isBotValid(String botName) {
        BotInstance bot = getBot(botName);
        return bot != null && bot.active && bot.player != null && bot.player.isAlive();
    }

    /**
     * Cleanup stale bots (offline players, inactive).
     */
    public void cleanupInactiveBots() {
        Iterator<Map.Entry<String, BotInstance>> iter = activeBots.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, BotInstance> entry = iter.next();
            BotInstance bot = entry.getValue();
            if (!bot.active || bot.player == null || !bot.player.isAlive()) {
                LOGGER.info("Cleaning up inactive bot: {}", bot.botName);
                iter.remove();
            }
        }
    }

    public int getActiveBotCount() { return activeBots.size(); }
    public ServerLevel getLevel() { return level; }
}
