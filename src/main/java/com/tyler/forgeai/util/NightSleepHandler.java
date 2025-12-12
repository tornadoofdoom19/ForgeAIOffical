package com.tyler.forgeai.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BedBlock;
import com.tyler.forgeai.core.BotCommunicationManager;
import com.tyler.forgeai.core.TaskManager;
import com.tyler.forgeai.ai.SharedWorldMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NightSleepHandler: Manages bot sleep cycles during nighttime.
 * - Detects night (sky brightness < threshold)
 * - Finds nearest bed in world or from SharedWorldMemory
 * - If important task running, asks owner for permission to pause
 * - Sleeps when bed is ready
 * - Resumes task at morning
 */
public class NightSleepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-sleep");

    private final String botName;
    private final SharedWorldMemory sharedMemory;
    private final BotCommunicationManager comms;
    private final TaskManager taskManager;

    private boolean sleepRequested = false;
    private String sleepPermissionQuestionId = null;
    private BlockPos targetBedPos = null;

    public static final int NIGHT_START_TICKS = 12500;  // Minecraft: night starts around tick 12500
    public static final int NIGHT_END_TICKS = 23500;    // Night ends around tick 23500
    public static final int BED_SEARCH_RADIUS = 256;    // Search up to 256 blocks away

    public NightSleepHandler(String botName, SharedWorldMemory sharedMemory, 
                             BotCommunicationManager comms, TaskManager taskManager) {
        this.botName = botName;
        this.sharedMemory = sharedMemory;
        this.comms = comms;
        this.taskManager = taskManager;
        LOGGER.info("NightSleepHandler initialized for bot: {}", botName);
    }

    /**
     * Main tick: check if it's nighttime and handle sleep logic.
     */
    public void tick(MinecraftServer server, ServerPlayer player) {
        if (player == null || player.level() == null) return;

        ServerLevel level = (ServerLevel) player.level();
        long dayTime = level.getDayTime();
        boolean isNight = isNighttime(dayTime);

        if (!isNight) {
            // Day time: if sleeping, wake up and resume tasks
            if (sleepRequested) {
                wakeUpAndResume(player);
            }
            return;
        }

        // Nighttime: check if we need to sleep
        if (sleepRequested) {
            // Already sleeping or waiting for permission
            handleSleepInProgress(player);
        } else {
            // Not yet in sleep cycle; check if we should request it
            requestSleep(server, player);
        }
    }

    /**
     * Check if current time is nighttime.
     */
    public static boolean isNighttime(long dayTime) {
        long timeOfDay = dayTime % 24000;
        return timeOfDay >= NIGHT_START_TICKS || timeOfDay <= NIGHT_END_TICKS;
    }

    /**
     * Request sleep: find bed and ask owner if task is important.
     */
    private void requestSleep(MinecraftServer server, ServerPlayer player) {
        // Find nearest bed
        SharedWorldMemory.WorldLocation bedLoc = sharedMemory.findNearestLocation(
            "bed", player.getBlockX(), player.getBlockY(), player.getBlockZ()
        );

        if (bedLoc != null) {
            targetBedPos = new BlockPos(bedLoc.x, bedLoc.y, bedLoc.z);
            LOGGER.info("Found registered bed at {},{},{}", bedLoc.x, bedLoc.y, bedLoc.z);
        } else {
            // Scan for nearest bed block in radius
            targetBedPos = BlockInteractionUtils.findNearestBlock(player, BedBlock.class, BED_SEARCH_RADIUS);
            if (targetBedPos != null) {
                // Register this bed for future use
                sharedMemory.registerLocation(
                    "bed_" + targetBedPos.getX() + "_" + targetBedPos.getZ(),
                    targetBedPos.getX(), targetBedPos.getY(), targetBedPos.getZ(),
                    "bed", botName
                );
                LOGGER.info("Scanned and found bed at {}", targetBedPos);
            }
        }

        if (targetBedPos == null) {
            LOGGER.debug("No bed found nearby; bot will skip sleep this cycle");
            return;
        }

        // Check if task is important
        TaskManager.Task currentTask = taskManager.getCurrentTask();
        if (currentTask != null && currentTask.priority.priority >= TaskManager.TaskPriority.HIGH.priority) {
            // Ask owner if we should pause and sleep
            String questionId = comms.askOwnerYesNo(
                server,
                String.format("It's nighttime. Pause current task (%s) to sleep?", currentTask.commandType)
            );
            sleepPermissionQuestionId = questionId;
            LOGGER.info("Asked owner for permission to pause task; question id: {}", questionId);
            return;
        }

        // For normal/low priority tasks, just pause and sleep
        sleepRequested = true;
        if (currentTask != null) {
            taskManager.pauseCurrentTask();
            LOGGER.info("Paused normal priority task for nighttime sleep");
        }
        comms.broadcastStatus(server, "Heading to bed for the night.");
    }

    /**
     * Handle sleep in progress: check permissions, navigate to bed, sleep.
     */
    private void handleSleepInProgress(ServerPlayer player) {
        if (sleepPermissionQuestionId != null) {
            String response = comms.getQuestionResponse(sleepPermissionQuestionId);
            if (response == null) {
                return;  // Still waiting for response
            }

            if (response.equals("yes")) {
                // Owner approved: pause task and go to bed
                taskManager.pauseCurrentTask();
                LOGGER.info("Owner approved sleep; pausing task");
                sleepPermissionQuestionId = null;
            } else if (response.equals("no") || response.equals("timeout")) {
                // Owner declined or timeout: resume task, skip sleep
                LOGGER.info("Sleep request declined or timed out; skipping sleep");
                sleepRequested = false;
                sleepPermissionQuestionId = null;
                return;
            }
        }

        // Navigate to bed and sleep
        if (targetBedPos != null) {
            double dist = player.distanceToSqr(targetBedPos.getX(), targetBedPos.getY(), targetBedPos.getZ());
            if (dist < 4.0) {
                // Close enough: try to sleep
                if (BlockInteractionUtils.interactWithBed(player, targetBedPos)) {
                    LOGGER.info("Bot sleeping in bed at {}", targetBedPos);
                    // Bot will remain sleeping until wake-up tick
                }
            } else {
                // Minimal navigation: look and step toward bed
                try {
                    com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, targetBedPos);
                    float speed = 0.2f;
                    com.tyler.forgeai.util.PlayerActionUtils.moveForward(player, speed);
                    LOGGER.debug("Bot navigating to bed at {} (distance: {})", targetBedPos, Math.sqrt(dist));
                } catch (Exception e) {
                    LOGGER.debug("Error navigating to bed: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Wake up in the morning and resume paused task.
     */
    private void wakeUpAndResume(ServerPlayer player) {
        sleepRequested = false;
        sleepPermissionQuestionId = null;
        targetBedPos = null;

        // Resume paused task
        taskManager.resumeCurrentTask();
        LOGGER.info("Bot woke up; resuming paused task");

        // Notify owner
        if (comms != null) {
            // Note: server reference not available here; would need to pass it in or queue message
            LOGGER.info("Morning: resuming tasks");
        }
    }

    public boolean isSleeping() { return sleepRequested; }
    public BlockPos getTargetBedPos() { return targetBedPos; }
}
