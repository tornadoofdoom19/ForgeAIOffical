package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.tyler.forgeai.config.FriendsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TaskLockManager: Enforces task ownership and prevents non-owners from modifying tasks.
 * - Only the owner can pause, resume, or cancel tasks
 * - Friends can suggest tasks, but owner approves
 * - Locked tasks cannot be modified by other bots or unauthorized players
 */
public class TaskLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-task-lock");

    private final String botName;
    private final FriendsList friendsList;

    private TaskManager.Task lockedTask = null;  // Current locked task
    private String taskLockOwner = null;         // Who locked the current task

    public TaskLockManager(String botName, FriendsList friendsList) {
        this.botName = botName;
        this.friendsList = friendsList;
        LOGGER.info("TaskLockManager initialized for bot: {}", botName);
    }

    /**
     * Try to execute a command from a player.
     * Returns true if command is allowed, false otherwise (and sends rejection message).
     */
    public boolean authorizeCommand(String playerName, String commandType, MinecraftServer server) {
        // Primary owner can always execute commands
        if (friendsList.isPrimaryOwner(playerName)) {
            return true;
        }

        // Check if task is locked by someone else
        if (isTaskLocked()) {
            if (!taskLockOwner.equalsIgnoreCase(playerName)) {
                // Reject: task is locked to someone else
                ServerPlayer requester = server.getPlayerList().getPlayerByName(playerName);
                if (requester != null) {
                    String msg = String.format(
                        "§c[%s]§r Task is locked by %s. Cannot execute %s until task is released.",
                        botName, taskLockOwner, commandType
                    );
                    requester.sendSystemMessage(Component.literal(msg));
                }
                LOGGER.warn("{} attempted {} but task is locked to {}", playerName, commandType, taskLockOwner);
                return false;
            }
        }

        // Trusted friends can execute commands if no lock
        if (friendsList.isTrusted(playerName)) {
            return true;
        }

        // Reject: untrusted player
        ServerPlayer requester = server.getPlayerList().getPlayerByName(playerName);
        if (requester != null) {
            String msg = String.format("§c[%s]§r You are not trusted. Cannot execute %s.", botName, commandType);
            requester.sendSystemMessage(Component.literal(msg));
        }
        LOGGER.warn("{} attempted {} but is not trusted", playerName, commandType);
        return false;
    }

    /**
     * Lock a task to a specific owner (when they issue an exclusive command).
     */
    public void lockTask(TaskManager.Task task, String owner) {
        this.lockedTask = task;
        this.taskLockOwner = owner;
        LOGGER.info("Task locked to owner: {} for bot: {}", owner, botName);
    }

    /**
     * Unlock current task (when owner releases it or task completes).
     */
    public void unlockTask() {
        if (lockedTask != null) {
            LOGGER.info("Task unlocked for bot: {}", botName);
        }
        this.lockedTask = null;
        this.taskLockOwner = null;
    }

    /**
     * Check if a task is currently locked.
     */
    public boolean isTaskLocked() {
        return lockedTask != null && taskLockOwner != null;
    }

    /**
     * Get the player who locked the current task.
     */
    public String getTaskLockOwner() {
        return taskLockOwner;
    }

    /**
     * Force unlock a task (only callable by primary owner).
     */
    public void forceUnlock(String playerName) {
        if (friendsList.isPrimaryOwner(playerName)) {
            unlockTask();
            LOGGER.info("Task forcefully unlocked by primary owner: {}", playerName);
        } else {
            LOGGER.warn("{} attempted to force unlock task (not primary owner)", playerName);
        }
    }
}
