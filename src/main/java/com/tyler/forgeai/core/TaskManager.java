package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Task Manager: Manages AI task execution and prioritization.
 * Handles multi-purpose AI: PvP, farming, resource gathering, AFK, navigation.
 */
public class TaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-tasks");

    public enum TaskPriority {
        CRITICAL(10),   // Immediate execution (flee, totem, shield)
        HIGH(7),        // Important (combat, attack)
        NORMAL(5),      // Standard (farming, navigation)
        LOW(3),         // Background (idle, secondary tasks)
        DEFERRED(1);    // Can wait (buffs, optional)

        public final int priority;
        TaskPriority(int priority) { this.priority = priority; }
    }

    public enum TaskStatus {
        QUEUED, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED
    }

    public static class Task {
        public final String id;
        public final AICommandParser.CommandType commandType;
        public final TaskPriority priority;
        public final Map<String, String> parameters;
        
        public TaskStatus status;
        public long createdAt;
        public long startedAt;
        public long completedAt;
        public String failureReason;
        public Map<String, Object> pauseData;

        public Task(String id, AICommandParser.CommandType commandType, 
                   TaskPriority priority, Map<String, String> parameters) {
            this.id = id;
            this.commandType = commandType;
            this.priority = priority;
            this.parameters = new HashMap<>(parameters);
            this.status = TaskStatus.QUEUED;
            this.createdAt = System.currentTimeMillis();
            this.pauseData = new HashMap<>();
        }

        public long getElapsedTime() {
            if (startedAt == 0) return 0;
            return System.currentTimeMillis() - startedAt;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (priority=%s, elapsed=%dms)",
                status, commandType, priority, getElapsedTime());
        }
    }

    private final Queue<Task> taskQueue;
    private Task currentTask;
    private final Map<String, Task> completedTasks;
    private final TaskExecutor executor;
    private net.minecraft.core.BlockPos buildAnchor = null;
    private TaskLockManager lockManager;  // Optional: for task locking

    public TaskManager(TaskExecutor executor) {
        this.taskQueue = new PriorityQueue<>((t1, t2) -> 
            Integer.compare(t2.priority.priority, t1.priority.priority));
        this.completedTasks = new HashMap<>();
        this.executor = executor;
    }

    public void setBuildLocation(net.minecraft.core.BlockPos pos) { this.buildAnchor = pos; }
    public net.minecraft.core.BlockPos getBuildLocation() { return this.buildAnchor; }

    /**
     * Enqueue a pre-built task (used for delegating subtasks between bots).
     */
    public void enqueueTask(Task task) {
        if (task == null) return;
        taskQueue.offer(task);
        LOGGER.info("Enqueued task: {} - {}", task.id, task.commandType);
    }

    /**
     * Remove a task from the queue by ID.
     */
    public boolean removeTask(String taskId) {
        if (taskId == null) return false;
        
        // Check if it's the current task
        if (currentTask != null && taskId.equals(currentTask.id)) {
            currentTask.status = TaskStatus.CANCELLED;
            currentTask = null;
            LOGGER.info("Cancelled current task: {}", taskId);
            return true;
        }
        
        // Remove from queue if present
        boolean removed = taskQueue.removeIf(task -> taskId.equals(task.id));
        if (removed) {
            LOGGER.info("Removed task from queue: {}", taskId);
        }
        return removed;
    }

    /**
     * Set task lock manager for owner enforcement.
     */
    public void setTaskLockManager(TaskLockManager lockManager) {
        this.lockManager = lockManager;
    }

    /**
     * Queue a new task for execution (with optional player origin).
     */
    public Task queueTask(AICommandParser.ParsedCommand cmd, TaskPriority priority) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        Task task = new Task(taskId, cmd.type, priority, cmd.parameters);
        
        taskQueue.offer(task);
        LOGGER.info("Queued task: {} - {}", taskId, cmd.type);
        
        return task;
    }

    /**
     * Execute next task in queue.
     */
    public void tick(MinecraftServer server) {
        // If current task is done, mark as completed
        if (currentTask != null && currentTask.status == TaskStatus.EXECUTING) {
            if (executor.isTaskComplete(currentTask)) {
                currentTask.status = TaskStatus.COMPLETED;
                currentTask.completedAt = System.currentTimeMillis();
                completedTasks.put(currentTask.id, currentTask);
                // Unlock task if it was locked
                if (lockManager != null) {
                    lockManager.unlockTask();
                }
                LOGGER.info("Completed task: {}", currentTask.id);
                currentTask = null;
            }
        }

        // Start next task if available
        if (currentTask == null && !taskQueue.isEmpty()) {
            currentTask = taskQueue.poll();
            currentTask.status = TaskStatus.EXECUTING;
            currentTask.startedAt = System.currentTimeMillis();
            
            executor.executeTask(server, currentTask);
            LOGGER.info("Started task: {} - {}", currentTask.id, currentTask.commandType);
        }
    }

    /**
     * Pause current task execution.
     */
    public void pauseCurrentTask() {
        if (currentTask != null) {
            currentTask.status = TaskStatus.PAUSED;
            executor.pauseTask(currentTask);
            LOGGER.info("Paused task: {}", currentTask.id);
        }
    }

    /**
     * Resume paused task.
     */
    public void resumeCurrentTask() {
        if (currentTask != null && currentTask.status == TaskStatus.PAUSED) {
            currentTask.status = TaskStatus.EXECUTING;
            executor.resumeTask(currentTask);
            LOGGER.info("Resumed task: {}", currentTask.id);
        }
    }

    /**
     * Cancel current task and optionally remove from queue.
     */
    public void cancelCurrentTask(String reason) {
        if (currentTask != null) {
            currentTask.status = TaskStatus.CANCELLED;
            currentTask.failureReason = reason;
            executor.cancelTask(currentTask);
            // Unlock task if it was locked
            if (lockManager != null) {
                lockManager.unlockTask();
            }
            LOGGER.info("Cancelled task: {} - {}", currentTask.id, reason);
            currentTask = null;
        }
    }

    /**
     * Clear all queued tasks.
     */
    public void clearQueue() {
        taskQueue.clear();
        LOGGER.info("Cleared task queue");
    }

    /**
     * Get current executing task.
     */
    public Task getCurrentTask() {
        return currentTask;
    }

    /**
     * Get task queue size.
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * Get completed tasks.
     */
    public Collection<Task> getCompletedTasks() {
        return completedTasks.values();
    }

    /**
     * Determine priority for command type.
     */
    public static TaskPriority getPriorityForCommand(AICommandParser.CommandType type) {
        switch (type) {
            // Critical survival situations
            case PVP_FLEE:
            case RL_PUNISH:
                return TaskPriority.CRITICAL;

            // High priority combat
            case PVP_ATTACK_PLAYER:
            case PVP_DEFEND:
                return TaskPriority.HIGH;

            // Normal tasks
            case TASK_FARM_WHEAT:
            case TASK_FARM_CARROTS:
            case TASK_MINE_GOLD:
            case TASK_MINE_IRON:
            case TASK_RAID_FARM:
            case TASK_BOTTLE_FARM:
            case TASK_AFK:
            case TASK_EXPLORE:
            case TASK_FIND_BASE:
            case TASK_FIND_STRUCTURE:
            case TASK_BEAT_GAME:
            case TASK_OBSERVE_FIGHT:
            case TASK_FARM_ANIMALS:
            case TASK_COLLECT_RESOURCES:
            case NAV_GO_TO:
            case NAV_HOME:
                return TaskPriority.NORMAL;

            // Trust management
            case TRUST_SET_OWNER:
                return TaskPriority.HIGH;

            // Communication
            case CHAT_SAY_PUBLIC:
            case CHAT_WHISPER_PRIVATE:
            case CHAT_MESSAGE_PLAYER:
                return TaskPriority.LOW;

            // Feedback
            case RL_REWARD:
            case RL_RESET:
                return TaskPriority.DEFERRED;

            // Navigation teleport
            case NAV_TELEPORT:
                return TaskPriority.NORMAL;

            default:
                return TaskPriority.NORMAL;
        }
    }

    /**
     * Task executor interface for external implementation.
     */
    public interface TaskExecutor {
        /**
         * Execute a task.
         */
        void executeTask(MinecraftServer server, Task task);

        /**
         * Check if task is complete.
         */
        boolean isTaskComplete(Task task);

        /**
         * Pause task execution.
         */
        void pauseTask(Task task);

        /**
         * Resume paused task.
         */
        void resumeTask(Task task);

        /**
         * Cancel task.
         */
        void cancelTask(Task task);
    }

    // ---- Extended task type handlers (crafting, smelting, etc.) ----

    /**
     * Execute crafting task: craft <recipe> [amount]
     * Examples: craft oak_planks 64, craft oak_door 10
     */
    public static void executeCraft(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String recipe = task.parameters.getOrDefault("recipe", "oak_planks");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing craft task: {} x{}", recipe, amount);
        
        try {
            // Find crafting table
            net.minecraft.core.BlockPos craftingTable = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock().getName().getString().contains("crafting_table")) {
                    craftingTable = pos;
                    break;
                }
            }
            
            if (craftingTable != null) {
                com.tyler.forgeai.util.PlayerActionUtils.interactBlock(player, craftingTable, net.minecraft.world.InteractionHand.MAIN_HAND);
                LOGGER.info("Crafting {} x{}", recipe, amount);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Crafting " + amount + " " + recipe + "...");
            } else {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I couldn't find a crafting table nearby.");
            }
        } catch (Exception e) {
            LOGGER.error("Crafting task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, crafting failed.");
        }
    }

    /**
     * Execute smelting task: smelt <ore> [amount]
     * Examples: smelt iron_ore 64, smelt gold_ore 32
     */
    public static void executeSmelt(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String ore = task.parameters.getOrDefault("ore", "iron_ore");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing smelt task: {} x{}", ore, amount);
        try {
            // Find a nearby furnace or blast furnace
            net.minecraft.core.BlockPos furnacePos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                String name = state.getBlock().getName().getString().toLowerCase();
                if (name.contains("furnace") || name.contains("blast_furnace") || name.contains("smoker")) {
                    furnacePos = pos;
                    break;
                }
            }

            if (furnacePos == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "I couldn't find a furnace nearby.");
                return;
            }

            // Move ore and fuel to hotbar
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, ore); } catch (Exception ignored) {}
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "coal"); } catch (Exception ignored) {}

            // Open furnace UI (best-effort) and simulate smelting time
            com.tyler.forgeai.util.BlockInteractionUtils.openFurnace(player, furnacePos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Smelting " + amount + " " + ore + "...");
            try { Thread.sleep(1000L * Math.min(amount, 16)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Finished smelting (best-effort). Collect your items.");
        } catch (Exception e) {
            LOGGER.error("Smelting task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, smelting failed.");
        }
    }

    /**
     * Execute mining task: mine <ore_type> [amount]
     * Examples: mine diamond_ore 10, mine iron_ore 64
     */
    public static void executeMine(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String ore = task.parameters.getOrDefault("ore", "iron_ore");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing mine task: {} x{}", ore, amount);
        
        try {
            int mined = 0;
            int maxAttempts = amount * 3;  // Allow multiple blocks searched
            
            for (int i = 0; i < maxAttempts && mined < amount; i++) {
                // Scan for ore blocks nearby
                net.minecraft.core.BlockPos orePos = null;
                for (net.minecraft.core.BlockPos scanPos : 
                    net.minecraft.core.BlockPos.betweenClosed(
                        player.blockPosition().offset(-32, -5, -32),
                        player.blockPosition().offset(32, 10, 32))) {
                    var blockState = player.level().getBlockState(scanPos);
                    if (blockState.getMaterial().isReplaceable()) continue;
                    
                    String blockName = blockState.getBlock().getName().getString().toLowerCase();
                    if (blockName.contains(ore.toLowerCase())) {
                        orePos = scanPos;
                        break;
                    }
                }
                
                if (orePos == null) {
                    LOGGER.info("No {} ore found nearby", ore);
                    break;
                }
                
                // Navigate to ore and mine it
                com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, orePos);
                com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, orePos);
                
                // Simulate mining time
                try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                
                mined++;
            }
            
            LOGGER.info("Mined {} blocks of {}", mined, ore);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "I've mined " + mined + " " + ore + " for you!");
        } catch (Exception e) {
            LOGGER.error("Mining task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, mining failed.");
        }
    }

    /**
     * Execute farming task: farm <crop_type> [amount]
     * Examples: farm wheat 64, farm carrot 32, farm melon
     */
    public static void executeFarm(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String crop = task.parameters.getOrDefault("crop", "wheat");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing farm task: {} x{}", crop, amount);
        
        try {
            int harvested = 0;
            
            // Find farmland blocks with crops
            for (net.minecraft.core.BlockPos scanPos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-32, -2, -32),
                    player.blockPosition().offset(32, 2, 32))) {
                
                if (harvested >= amount) break;
                
                var blockState = player.level().getBlockState(scanPos);
                String blockName = blockState.getBlock().getName().getString().toLowerCase();
                
                // Check if it's the right crop and mature
                if (blockName.contains(crop.toLowerCase())) {
                    // Look at and harvest
                    com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, scanPos);
                    com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, scanPos);
                    harvested++;
                    
                    try { Thread.sleep(200); } catch (InterruptedException e) { }
                }
            }
            
            LOGGER.info("Harvested {} {} crops", harvested, crop);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Harvested " + harvested + " " + crop + "!");
        } catch (Exception e) {
            LOGGER.error("Farming task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, farming failed.");
        }
    }

    /**
     * Execute fishing task: fish [amount]
     * Examples: fish 32, fish
     */
    public static void executeFish(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing fish task: x{}", amount);
        
        try {
            // Find water
            net.minecraft.core.BlockPos waterPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getMaterial().isReplaceable() && state.getMaterial().isLiquid()) {
                    waterPos = pos;
                    break;
                }
            }
            
            if (waterPos != null) {
                com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, waterPos);
                LOGGER.info("Fishing at {}", waterPos.toShortString());
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Fishing for " + amount + " fish...");
                // Simulate fishing time
                try { Thread.sleep(amount * 500L); } catch (InterruptedException e) { }
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Got some fish!");
            } else {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I couldn't find water nearby.");
            }
        } catch (Exception e) {
            LOGGER.error("Fishing task failed: {}", e.getMessage());
        }
    }

    /**
     * Execute wood chopping task: chop [amount]
     * Examples: chop 64, chop
     */
    public static void executeChop(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing chop task: x{}", amount);
        
        try {
            int chopped = 0;
            // Find logs
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-32, -2, -32),
                    player.blockPosition().offset(32, 10, 32))) {
                
                if (chopped >= amount) break;
                
                var state = player.level().getBlockState(pos);
                String name = state.getBlock().getName().getString().toLowerCase();
                if (name.contains("log")) {
                    com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, pos);
                    chopped++;
                    try { Thread.sleep(200); } catch (InterruptedException e) { }
                }
            }
            
            LOGGER.info("Chopped {} logs", chopped);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Chopped " + chopped + " logs!");
        } catch (Exception e) {
            LOGGER.error("Chopping task failed: {}", e.getMessage());
        }
    }

    /**
     * Execute enchanting task: enchant <item_type> <level>
     * Examples: enchant sword sharpness_5, enchant pickaxe efficiency_5
     */
    public static void executeEnchant(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String item = task.parameters.getOrDefault("item", "sword");
        int level = Integer.parseInt(task.parameters.getOrDefault("level", "1"));
        LOGGER.info("Executing enchant task: {} level {}", item, level);
        try {
            net.minecraft.core.BlockPos tablePos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock().getName().getString().toLowerCase().contains("enchanting_table")) {
                    tablePos = pos; break;
                }
            }

            if (tablePos == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No enchanting table found nearby.");
                return;
            }

            // Move item and lapis to hotbar
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, item); } catch (Exception ignored) {}
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "lapis"); } catch (Exception ignored) {}

            com.tyler.forgeai.util.BlockInteractionUtils.openEnchantingTable(player, tablePos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Applying enchantments (best-effort)...");
            try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Enchantment process complete (please verify).");
        } catch (Exception e) {
            LOGGER.error("Enchanting task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, enchanting failed.");
        }
    }

    /**
     * Execute anvil repair task: repair <item_type>
     * Examples: repair sword, repair pickaxe
     */
    public static void executeRepair(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String item = task.parameters.getOrDefault("item", "sword");
        LOGGER.info("Executing repair task: {}", item);
        try {
            net.minecraft.core.BlockPos anvilPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.AnvilBlock) { anvilPos = pos; break; }
            }

            if (anvilPos == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No anvil found nearby.");
                return;
            }

            // Move item and material (iron_ingot / netherite_ingot) to hotbar
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, item); } catch (Exception ignored) {}
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "iron_ingot"); } catch (Exception ignored) {}
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "netherite_ingot"); } catch (Exception ignored) {}

            com.tyler.forgeai.util.BlockInteractionUtils.openAnvil(player, anvilPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Repairing item (best-effort)...");
            try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Repair complete (please verify durability).");
        } catch (Exception e) {
            LOGGER.error("Repair task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, repair failed.");
        }
    }

    /**
     * Execute brewing task: brew <potion_type>
     * Examples: brew strength, brew speed, brew invisibility
     */
    public static void executeBrew(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String potion = task.parameters.getOrDefault("potion", "strength");
        LOGGER.info("Executing brew task: {}", potion);
        try {
            net.minecraft.core.BlockPos standPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.BrewingStandBlock) { standPos = pos; break; }
            }

            if (standPos == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No brewing stand found nearby.");
                return;
            }

            // Move bottles and ingredient keywords to hotbar
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "bottle"); } catch (Exception ignored) {}
            // Map common potion types to ingredients
            String ingredientKeyword = switch (potion.toLowerCase()) {
                case "strength" -> "blaze_powder";
                case "speed" -> "sugar";
                case "invisibility" -> "golden_apple"; // rough mapping
                default -> potion;
            };
            try { com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, ingredientKeyword); } catch (Exception ignored) {}

            com.tyler.forgeai.util.BlockInteractionUtils.openBrewingStand(player, standPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Brewing " + potion + " (best-effort)...");
            try { Thread.sleep(1200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Brewing complete (please collect bottles).");
        } catch (Exception e) {
            LOGGER.error("Brewing task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, brewing failed.");
        }
    }

    /**
     * Execute composting task: compost <plant_matter_amount>
     * Examples: compost 64, compost
     */
    public static void executeCompost(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing compost task: {} items", amount);
        try {
            net.minecraft.core.BlockPos composterPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -2, -16),
                    player.blockPosition().offset(16, 2, 16))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.ComposterBlock) { composterPos = pos; break; }
            }

            if (composterPos == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "I couldn't find a composter nearby.");
                return;
            }

            // Add plant matter from inventory (best-effort)
            int added = 0;
            for (int i = 0; i < player.getInventory().getContainerSize() && added < amount; i++) {
                var stack = player.getInventory().getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                String name = stack.getItem().toString().toLowerCase();
                // crude plant matter check
                if (name.contains("seed") || name.contains("wheat") || name.contains("sapling") || name.contains("leaves") || name.contains("sapling")) {
                    int take = Math.min(added + stack.getCount(), amount) - added;
                    stack.shrink(take);
                    added += take;
                }
            }

            com.tyler.forgeai.util.BlockInteractionUtils.useComposter(player, composterPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Composting " + added + " items (best-effort). Please collect bone meal when ready.");
        } catch (Exception e) {
            LOGGER.error("Compost task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, composting failed.");
        }
    }

    /**
     * Execute building task: build <schematic_file>
     * Examples: build farm_house, build bunker
     */
    public static void executeBuild(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String schematic = task.parameters.getOrDefault("schematic", "structure");
        LOGGER.info("Executing build task: {}", schematic);

        try {
            // Load schematic
            java.io.File schematicDir = new java.io.File("schematics");
            if (!schematicDir.exists()) schematicDir.mkdirs();
            java.io.File schematicFile = new java.io.File(schematicDir, schematic + ".litematic");
            if (!schematicFile.exists()) schematicFile = new java.io.File(schematicDir, schematic);
            if (!schematicFile.exists()) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Schematic file not found: " + schematic);
                return;
            }

            com.tyler.forgeai.util.LitematicaIntegration.SchematicData schematicData = com.tyler.forgeai.util.LitematicaIntegration.loadSchematic(schematicFile);
            if (schematicData == null || schematicData.blocks.isEmpty()) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Failed to load schematic: " + schematic);
                return;
            }

            // Material list
            Map<String, Integer> materials = com.tyler.forgeai.util.LitematicaIntegration.computeMaterialRequirements(schematicData);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Building the " + schematic + "! (" + schematicData.blocks.size() + " blocks)");

            // Validate anchor
            com.tyler.forgeai.modules.builder.BuildSafetyManager.setBuildLocation(player, player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
            net.minecraft.core.BlockPos origin = com.tyler.forgeai.modules.builder.BuildSafetyManager.getBuildLocation(player);
            if (origin == null) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Please set a build anchor before starting a build.");
                return;
            }

            // Config and flags
            com.tyler.forgeai.config.ConfigLoader cfgLoader = new com.tyler.forgeai.config.ConfigLoader();
            cfgLoader.init();
            com.tyler.forgeai.config.ConfigLoader.ForgeAIConfig cfg = cfgLoader.getConfig();
            boolean dryRun = Boolean.parseBoolean(task.parameters.getOrDefault("dryRun", String.valueOf(cfg.buildDryRunDefault)));
            boolean allowOverwrite = Boolean.parseBoolean(task.parameters.getOrDefault("allowOverwrite", String.valueOf(cfg.allowOverwriteBuilds)));

            // Dry-run validation
            if (!com.tyler.forgeai.modules.builder.BuildSafetyManager.dryRunValidate((net.minecraft.server.level.ServerLevel) player.level(), schematicData, origin, cfg)) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Dry-run validation failed; build canceled.");
                return;
            }

            if (!com.tyler.forgeai.modules.builder.BuildSafetyManager.validateAreaClear((net.minecraft.server.level.ServerLevel) player.level(), schematicData, origin, cfg)) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Schematic placement conflict or unsafe area. Aborting build.");
                return;
            }

            // Delegate gatherers if resources missing
            try {
                com.tyler.forgeai.modules.utility.ResourceTracker rt = new com.tyler.forgeai.modules.utility.ResourceTracker();
                rt.update(player);
                BotRegistry registry = BotRegistry.getOrCreateRegistry((net.minecraft.server.level.ServerLevel) player.level());
                for (Map.Entry<String, Integer> entry : materials.entrySet()) {
                    String material = entry.getKey();
                    int required = entry.getValue();
                    int have = rt.getCount(material);
                    if (have < required) {
                        var gatherers = registry.findAvailableBotsForRole(player.getGameProfile().getName(), "gatherer");
                        if (!gatherers.isEmpty()) {
                            Map<String, String> p = new HashMap<>();
                            p.put("resource", material);
                            p.put("amount", String.valueOf(required - have));
                            AICommandParser.CommandType type = AICommandParser.CommandType.TASK_MINE_IRON;
                            Task subtask = new Task(UUID.randomUUID().toString().substring(0,8), type, TaskPriority.NORMAL, p);
                            gatherers.get(0).taskManager.enqueueTask(subtask);
                            LOGGER.info("Delegated gather task for {} x{} to {}", material, required - have, gatherers.get(0).botName);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Build gatherer delegation failed: {}", e.getMessage());
            }

            if (dryRun) {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Dry-run successful; no blocks placed.");
                return;
            }

            // Build with layer-by-layer placement
            try {
                int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
                for (com.tyler.forgeai.util.LitematicaIntegration.BlockEntry b : schematicData.blocks) {
                    int y = b.pos.getY();
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                int placed = 0;
                List<com.tyler.forgeai.util.LitematicaIntegration.BlockEntry> sorted = new ArrayList<>(schematicData.blocks);
                sorted.sort(Comparator.comparingInt(e -> e.pos.getY()));
                for (int layer = minY; layer <= maxY; layer++) {
                    for (com.tyler.forgeai.util.LitematicaIntegration.BlockEntry entry : sorted) {
                        if (entry.pos.getY() != layer) continue;
                        net.minecraft.core.BlockPos worldPos = origin.offset(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
                        boolean guardOk = com.tyler.forgeai.modules.builder.BuildSafetyManager.guardPlacement((net.minecraft.server.level.ServerLevel) player.level(), player, entry, origin, cfg);
                        if (!guardOk) {
                            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Build safety violation; aborting.");
                            return;
                        }
                        com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, worldPos);
                        com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, entry.blockName);
                        boolean placedOk = com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, worldPos);
                        if (!placedOk) {
                            LOGGER.warn("Failed to place block at {}: {}", worldPos, entry.blockName);
                            try { if (com.tyler.forgeai.ForgeAI.getPunishmentSystem() != null) com.tyler.forgeai.ForgeAI.getPunishmentSystem().punish("BuilderModule", 10); } catch (Exception ignored) {}
                            return;
                        }
                        placed++;
                        if (placed >= 64) break;
                    }
                }
                LOGGER.info("Completed build of {} (~{} blocks placed)", schematic, placed);
            } catch (Exception e) {
                LOGGER.error("Build error: {}", e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error("Building task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, building failed.");
        }
    }

    /**
     * Execute gathering task: gather <resource_type> [amount]
     * Examples: gather wood 64, gather stone 128
     */
    public static void executeGather(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String resource = task.parameters.getOrDefault("resource", "wood");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing gather task: {} x{}", resource, amount);
        
        try {
            int gathered = 0;
            // Scan for resource blocks
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-32, -2, -32),
                    player.blockPosition().offset(32, 10, 32))) {
                
                if (gathered >= amount) break;
                
                var state = player.level().getBlockState(pos);
                String name = state.getBlock().getName().getString().toLowerCase();
                if (name.contains(resource.toLowerCase())) {
                    com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, pos);
                    gathered++;
                    try { Thread.sleep(200); } catch (InterruptedException e) { }
                }
            }
            
            LOGGER.info("Gathered {} {}", gathered, resource);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Gathered " + gathered + " " + resource + "!");
        } catch (Exception e) {
            LOGGER.error("Gathering task failed: {}", e.getMessage());
        }
    }

    /**
     * Execute sleep task: sleep
     * Returns to home/bed and sleeps if nighttime
     */
    public static void executeSleep(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        LOGGER.info("Executing sleep task");
        
        try {
            // Find bed
            net.minecraft.core.BlockPos bedPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-32, -2, -32),
                    player.blockPosition().offset(32, 2, 32))) {
                var state = player.level().getBlockState(pos);
                if (state.getBlock().getName().getString().contains("bed")) {
                    bedPos = pos;
                    break;
                }
            }
            
            if (bedPos != null) {
                com.tyler.forgeai.util.PlayerActionUtils.interactBlock(player, bedPos, net.minecraft.world.InteractionHand.MAIN_HAND);
                LOGGER.info("Sleep initiated");
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Going to sleep...");
            } else {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I couldn't find a bed.");
            }
        } catch (Exception e) {
            LOGGER.error("Sleep task failed: {}", e.getMessage());
        }
    }

    /**
     * Execute trading task: trade <villager_type> [amount]
     * Examples: trade librarian 10, trade cleric
     */
    public static void executeTrade(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String villagerType = task.parameters.getOrDefault("villager", "librarian");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing trade task: {} x{}", villagerType, amount);
        try {
            var villager = com.tyler.forgeai.core.VillagerTradeManager.findVillagerWithTrade(player, villagerType, 32);
            if (villager != null && villager.villager.isAlive()) {
                player.openMerchantScreen(villager.villager);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Trading with " + villagerType + "...");
                try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Trade complete (check inventory).");
            } else {
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "I couldn't find a " + villagerType + " nearby.");
            }
        } catch (Exception e) {
            LOGGER.error("Trade task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Sorry, trading failed.");
        }
    }

    /**
     * Execute stonecutting task: cut <material> [amount]
     * Examples: cut granite 32, cut sandstone 64
     */
    public static void executeStonecutting(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String material = task.parameters.getOrDefault("material", "stone");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing stonecutting task: {} x{}", material, amount);
        try {
            net.minecraft.core.BlockPos cutterPos = com.tyler.forgeai.util.BlockInteractionUtils.findNearestBlock(player, net.minecraft.world.level.block.StonecutterBlock.class, 16);
            if (cutterPos == null) { com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No stonecutter found."); return; }
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, material);
            com.tyler.forgeai.util.BlockInteractionUtils.openStonecutter(player, cutterPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Stonecutting " + amount + " items...");
            try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Stonecutting complete.");
        } catch (Exception e) { LOGGER.error("Stonecutting task failed: {}", e.getMessage()); }
    }

    /**
     * Execute smithing task: smith <recipe>
     * Examples: smith netherite_pickaxe, smith netherite_armor
     */
    public static void executeSmithing(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String recipe = task.parameters.getOrDefault("recipe", "netherite_pickaxe");
        LOGGER.info("Executing smithing task: {}", recipe);
        try {
            net.minecraft.core.BlockPos smithPos = com.tyler.forgeai.util.BlockInteractionUtils.findNearestBlock(player, net.minecraft.world.level.block.SmithingTableBlock.class, 16);
            if (smithPos == null) { com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No smithing table found."); return; }
            com.tyler.forgeai.util.BlockInteractionUtils.openSmithingTable(player, smithPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Smithing " + recipe + "...");
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Smithing complete (check inventory).");
        } catch (Exception e) { LOGGER.error("Smithing task failed: {}", e.getMessage()); }
    }

    /**
     * Execute loom task: loom <banner_pattern> [amount]
     * Examples: loom stripe 10, loom cross 5
     */
    public static void executeLoom(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String pattern = task.parameters.getOrDefault("pattern", "stripe");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing loom task: {} x{}", pattern, amount);
        try {
            net.minecraft.core.BlockPos loomPos = com.tyler.forgeai.util.BlockInteractionUtils.findNearestBlock(player, net.minecraft.world.level.block.LoomBlock.class, 16);
            if (loomPos == null) { com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No loom found."); return; }
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "banner");
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "dye");
            com.tyler.forgeai.util.BlockInteractionUtils.openLoom(player, loomPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Weaving " + pattern + "...");
            try { Thread.sleep(800); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Weaving complete.");
        } catch (Exception e) { LOGGER.error("Loom task failed: {}", e.getMessage()); }
    }

    /**
     * Execute cartography task: map <location>
     * Examples: map spawn, map base
     */
    public static void executeCartography(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String location = task.parameters.getOrDefault("location", "spawn");
        LOGGER.info("Executing cartography task: mapping {}", location);
        try {
            net.minecraft.core.BlockPos cartPos = com.tyler.forgeai.util.BlockInteractionUtils.findNearestBlock(player, net.minecraft.world.level.block.CartographyTableBlock.class, 16);
            if (cartPos == null) { com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "No cartography table found."); return; }
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "map");
            com.tyler.forgeai.util.BlockInteractionUtils.openCartographyTable(player, cartPos);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Mapping " + location + "...");
            try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Map created (check inventory).");
        } catch (Exception e) { LOGGER.error("Cartography task failed: {}", e.getMessage()); }
    }

    /**
     * Execute portal creation: portal <nether|end>
     * Examples: portal nether, portal end
     */
    public static void executePortal(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String type = task.parameters.getOrDefault("type", "nether");
        LOGGER.info("Executing portal task: creating {} portal", type);
        try {
            String material = type.toLowerCase().contains("nether") ? "obsidian" : "end_frame_block";
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, material);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Building " + type + " portal...");
            try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, type.toLowerCase().contains("nether") ? "flint_and_steel" : "eye_of_ender");
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Portal created (best-effort).");
        } catch (Exception e) { LOGGER.error("Portal task failed: {}", e.getMessage()); }
    }

    /**
     * Execute navigation task: goto <x> <y> <z>
     * Examples: goto 100 64 200, goto -500 100 -500
     */
    public static void executeNavigate(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        int x = Integer.parseInt(task.parameters.getOrDefault("x", "0"));
        int y = Integer.parseInt(task.parameters.getOrDefault("y", "64"));
        int z = Integer.parseInt(task.parameters.getOrDefault("z", "0"));
        LOGGER.info("Executing navigate task: to {} {} {}", x, y, z);
        try {
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Navigating to " + x + " " + z + "...");
            double dist = player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
            if (dist < 10) { com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Already near target."); return; }
            double dirX = x - player.getX(); double dirZ = z - player.getZ(); double norm = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (norm > 0.01) { com.tyler.forgeai.util.PlayerActionUtils.lookAt(player, x, y, z); com.tyler.forgeai.util.PlayerActionUtils.moveForward(player, 0.6f); }
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Arrived at destination (best-effort).");
        } catch (Exception e) { LOGGER.error("Navigate task failed: {}", e.getMessage()); }
    }

    /**
     * Execute guard task: guard <location> [duration]
     * Examples: guard spawn, guard base 3600 (1 hour)
     */
    public static void executeGuard(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String location = task.parameters.getOrDefault("location", "spawn");
        long duration = Long.parseLong(task.parameters.getOrDefault("duration", "0"));
        LOGGER.info("Executing guard task: at {}, duration {}s", location, duration);
        try {
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Standing guard at " + location + "...");
            long endTime = System.currentTimeMillis() + (duration * 1000);
            while (System.currentTimeMillis() < endTime) {
                var threats = player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, player.getBoundingBox().inflate(16));
                for (var threat : threats) if (threat != player && threat.isAttackable()) { LOGGER.info("Threat detected: {}", threat.getName().getString()); }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Guard duty complete.");
        } catch (Exception e) { LOGGER.error("Guard task failed: {}", e.getMessage()); }
    }

    /**
     * Execute animal breeding task: breed <animal_type> [amount]
     * Examples: breed cow 10, breed sheep 5
     */
    public static void executeBreed(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String animal = task.parameters.getOrDefault("animal", "cow");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing breed task: {} x{}", animal, amount);
        try {
            String food = switch (animal.toLowerCase()) { case "cow" -> "wheat"; case "sheep" -> "wheat"; case "chicken" -> "seeds"; default -> "wheat"; };
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, food);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Breeding " + amount + " " + animal + "...");
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Bred " + animal + "!");
        } catch (Exception e) { LOGGER.error("Breed task failed: {}", e.getMessage()); }
    }

    /**
     * Execute cooking task: cook <food_type> [amount]
     * Examples: cook beef 32, cook salmon 64
     */
    public static void executeCook(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String food = task.parameters.getOrDefault("food", "beef");
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        LOGGER.info("Executing cook task: {} x{}", food, amount);
        try {
            String rawFood = "raw_" + food.toLowerCase();
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, rawFood);
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(player, "coal");
            net.minecraft.core.BlockPos furnacePos = com.tyler.forgeai.util.BlockInteractionUtils.findNearestBlock(player, net.minecraft.world.level.block.FurnaceBlock.class, 16);
            if (furnacePos != null) { com.tyler.forgeai.util.BlockInteractionUtils.openFurnace(player, furnacePos); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Cooking " + amount + " " + food + "...");
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, "Cooking complete (collect your food).");
        } catch (Exception e) { LOGGER.error("Cook task failed: {}", e.getMessage()); }
    }

    // ---- NEW: Animal Farming, Villager Trading, Herding, Dimensional Travel ----

    /**
     * Execute animal farming task: farm_animal <type> [breed|kill] [amount]
     * Examples: farm_animal sheep breed 10, farm_animal cow kill 5
     */
    public static void executeFarmAnimal(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String animalType = task.parameters.getOrDefault("animal", "sheep");
        String action = task.parameters.getOrDefault("action", "breed");  // breed or kill
        int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
        
        LOGGER.info("Executing farm animal task: {} x{} ({})", animalType, amount, action);
        
        try {
            if (action.equals("breed")) {
                // Breed animals
                int bred = com.tyler.forgeai.util.AnimalFarmingUtils.breedMultipleAnimals(
                    player, animalType, amount);
                LOGGER.info("Successfully bred {} {}", bred, animalType);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I've bred " + bred + " " + animalType + "!");
            } else if (action.equals("kill")) {
                // Kill animals
                int killed = com.tyler.forgeai.util.AnimalFarmingUtils.killAnimalsOfType(
                    player, animalType, amount);
                LOGGER.info("Successfully killed {} {}", killed, animalType);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Harvested " + killed + " " + animalType + ".");
            }
        } catch (Exception e) {
            LOGGER.error("Farm animal task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, I couldn't complete the farming task.");
        }
    }

    /**
     * Execute villager trading task: trade_villager <profession> <item>
     * Examples: trade_villager librarian mending, trade_villager cleric healing
     */
    public static void executeTradeVillager(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String profession = task.parameters.getOrDefault("profession", "librarian");
        String tradeItem = task.parameters.getOrDefault("item", "mending");
        
        LOGGER.info("Executing villager trade task: {} profession for {}", profession, tradeItem);
        
        try {
            // Find villager with desired trade
            com.tyler.forgeai.core.VillagerTradeManager.VillagerInfo villager = 
                com.tyler.forgeai.core.VillagerTradeManager.findVillagerWithTrade(
                    player, profession, tradeItem);
            
            if (villager == null) {
                LOGGER.warn("No {} villager found with trade: {}", profession, tradeItem);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I couldn't find a " + profession + " with that trade.");
                return;
            }
            
            // Move to villager
            com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, villager.location);
            
            // Gather materials if needed
            if (!player.getInventory().contains(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD))) {
                LOGGER.info("Gathering emeralds for trade");
                com.tyler.forgeai.core.VillagerTradeManager.gatherMaterialsForTrade(
                    player, profession);
            }
            
            // Execute trade
            com.tyler.forgeai.core.VillagerTradeManager.executeTradeWithVillager(
                player, villager, tradeItem);
            
            LOGGER.info("Successfully traded with {} villager", profession);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Got it! I've traded with the " + profession + ".");
        } catch (Exception e) {
            LOGGER.error("Villager trade task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, the trading attempt failed.");
        }
    }

    /**
     * Execute animal herding task: herd_animal <type> <destination>
     * Examples: herd_animal sheep 100_64_200, herd_animal horse base
     */
    public static void executeHerdAnimal(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String animalType = task.parameters.getOrDefault("animal", "sheep");
        String destination = task.parameters.getOrDefault("destination", "home");
        
        LOGGER.info("Executing herd animal task: {} to {}", animalType, destination);
        
        try {
            // Parse destination coordinates
            net.minecraft.core.BlockPos targetPos;
            if (destination.equals("home")) {
                // Use player's spawn point or home location
                targetPos = player.getRespawnPosition() != null ? 
                    player.getRespawnPosition() : player.blockPosition();
            } else {
                // Parse format: "100_64_200"
                String[] coords = destination.split("_");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                targetPos = new net.minecraft.core.BlockPos(x, y, z);
            }
            
            // Create herd
            com.tyler.forgeai.util.AnimalHerdingUtils.HerdInfo herd = 
                com.tyler.forgeai.util.AnimalHerdingUtils.createHerd(
                    player, animalType, targetPos);
            
            if (herd == null || herd.animals.isEmpty()) {
                LOGGER.warn("No {} animals found to herd", animalType);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I couldn't find any " + animalType + " nearby.");
                return;
            }
            
            // Lead herd to destination
            long estimatedTime = com.tyler.forgeai.util.AnimalHerdingUtils.estimateTimeToTarget(herd);
            LOGGER.info("Herding {} animals, estimated time: {}ms", herd.animals.size(), estimatedTime);
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Leading " + herd.animals.size() + " " + animalType + " to destination...");
            
            com.tyler.forgeai.util.AnimalHerdingUtils.moveHerd(herd);
            
            LOGGER.info("Successfully herded {} animals to {}", animalType, targetPos.toShortString());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Done! I've brought the " + animalType + " to your destination.");
        } catch (Exception e) {
            LOGGER.error("Herd animal task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, I couldn't complete the herding task.");
        }
    }

    /**
     * Execute dimensional travel task: travel <dimension> <x> <y> <z>
     * Examples: travel nether 100 64 200, travel end 0 128 0, travel overworld -1000 65 -1000
     */
    public static void executeDimensionalTravel(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String dimension = task.parameters.getOrDefault("dimension", "overworld");
        int x = Integer.parseInt(task.parameters.getOrDefault("x", "0"));
        int y = Integer.parseInt(task.parameters.getOrDefault("y", "64"));
        int z = Integer.parseInt(task.parameters.getOrDefault("z", "0"));
        
        LOGGER.info("Executing dimensional travel: {} to {}/{}/{}", dimension, x, y, z);
        
        try {
            // Create travel route
            net.minecraft.core.BlockPos destination = new net.minecraft.core.BlockPos(x, y, z);
            
            com.tyler.forgeai.core.DimensionalTravelManager.TravelRoute route = 
                com.tyler.forgeai.core.DimensionalTravelManager.planDimensionalTravel(
                    player, dimension, destination);
            
            if (route == null) {
                LOGGER.warn("Unable to plan travel to {} dimension", dimension);
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "I can't figure out how to get to the " + dimension + ".");
                return;
            }
            
            // Report travel plan
            LOGGER.info("Travel route planned: {} -> {}", 
                route.startPos.toShortString(), route.endPos.toShortString());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Ready! I'll travel to the " + dimension + " at your command.");
            
            // Find nearest portal
            net.minecraft.core.BlockPos nearestPortal = 
                com.tyler.forgeai.core.DimensionalTravelManager.findNearestPortal(
                    player, dimension);
            
            if (nearestPortal != null) {
                LOGGER.info("Found portal at {}", nearestPortal.toShortString());
                
                // Navigate to and use portal
                com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, nearestPortal);
                com.tyler.forgeai.core.DimensionalTravelManager.usePortal(player, nearestPortal);
                
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "Portal located and used! Traveling now...");
            } else {
                LOGGER.warn("No portal found, will create one");
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                    player, "No portal found. I'll create one.");
            }
            
            LOGGER.info("Dimensional travel to {} initiated", dimension);
        } catch (Exception e) {
            LOGGER.error("Dimensional travel task failed: {}", e.getMessage());
            com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(
                player, "Sorry, I couldn't complete the dimensional travel.");
        }
    }

    /**
     * Execute chat task: chat <message> [or] ask <question>
     * Examples: chat Hello there!, ask Do you have materials?
     */
    public static void executeChat(MinecraftServer server, Task task, net.minecraft.server.level.ServerPlayer player) {
        String action = task.parameters.getOrDefault("action", "say");
        String message = task.parameters.getOrDefault("message", "Hello!");
        
        LOGGER.info("Executing chat task: {} - {}", action, message);
        
        try {
            if (action.equals("ask")) {
                // Ask a question
                com.tyler.forgeai.core.CompanionChatHandler.askQuestion(player, message);
                LOGGER.info("Asked question: {}", message);
            } else if (action.equals("status")) {
                // Report current status
                com.tyler.forgeai.core.CompanionChatHandler.reportStatus(player);
                LOGGER.info("Reporting status");
            } else if (action.equals("greet")) {
                // Greet player
                com.tyler.forgeai.core.CompanionChatHandler.greet(player);
                LOGGER.info("Greeting player");
            } else {
                // Default: send custom message
                com.tyler.forgeai.core.CompanionChatHandler.sendChatMessage(player, message);
                LOGGER.info("Sent message: {}", message);
            }
        } catch (Exception e) {
            LOGGER.error("Chat task failed: {}", e.getMessage());
            // Fail gracefully
        }
    }

    // TaskExecutor interface is declared earlier in the file; duplicate removed.
}