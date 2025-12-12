package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * MultiBotDispatcher: Orchestrates complex multi-bot tasks (mining, building, gathering).
 * - Breaks down main task into subtasks
 * - Assigns subtasks to available bots
 * - Tracks progress and coordinates resources
 * - Examples: "Build farm house" -> assign 2 builders + 1 gatherer + 1 guard
 */
public class MultiBotDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-dispatcher");

    private final String coordinatorBotName;
    private final BotRegistry botRegistry;
    private final Map<String, MultiTaskJob> activeJobs = new HashMap<>();

    public static class MultiTaskJob {
        public String jobId;
        public String jobType;  // build, mine, gather, farm, etc.
        public String owner;    // Player who assigned job
        public List<SubTask> subtasks = new ArrayList<>();
        public Map<String, SubTaskAssignment> assignments = new HashMap<>();  // botName -> assignment
        public JobStatus status;
        public long createdAt;
        public long completedAt;

        public MultiTaskJob(String jobType, String owner) {
            this.jobId = UUID.randomUUID().toString().substring(0, 8);
            this.jobType = jobType;
            this.owner = owner;
            this.status = JobStatus.QUEUED;
            this.createdAt = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("Job{%s:%s, status=%s, subs=%d}", jobId, jobType, status, subtasks.size());
        }
    }

    public static class SubTask {
        public String taskId;
        public String type;      // mine, gather, build, guard, etc.
        public String target;    // ore type, block type, resource, etc.
        public int quantity;
        public SubTaskStatus status;

        public SubTask(String type, String target, int quantity) {
            this.taskId = UUID.randomUUID().toString().substring(0, 8);
            this.type = type;
            this.target = target;
            this.quantity = quantity;
            this.status = SubTaskStatus.PENDING;
        }

        @Override
        public String toString() {
            return String.format("Sub{%s:%s %s x%d, status=%s}", taskId, type, target, quantity, status);
        }
    }

    public static class SubTaskAssignment {
        public String subtaskId;
        public String assignedBot;
        public String status;  // assigned, in_progress, completed, failed
        public int progress;
        public long assignedAt;

        public SubTaskAssignment(String subtaskId, String assignedBot) {
            this.subtaskId = subtaskId;
            this.assignedBot = assignedBot;
            this.status = "assigned";
            this.progress = 0;
            this.assignedAt = System.currentTimeMillis();
        }
    }

    public enum JobStatus { QUEUED, EXECUTING, PAUSED, COMPLETED, FAILED, CANCELLED }
    public enum SubTaskStatus { PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED }

    public MultiBotDispatcher(String coordinatorBotName, BotRegistry botRegistry) {
        this.coordinatorBotName = coordinatorBotName;
        this.botRegistry = botRegistry;
        LOGGER.info("MultiBotDispatcher initialized for coordinator bot: {}", coordinatorBotName);
    }

    /**
     * Create a multi-bot job (e.g., "Build farm house").
     * Returns job ID for tracking.
     */
    public String createJob(String jobType, String owner) {
        MultiTaskJob job = new MultiTaskJob(jobType, owner);
        activeJobs.put(job.jobId, job);
        LOGGER.info("Created job: {} (type={}, owner={})", job.jobId, jobType, owner);
        return job.jobId;
    }

    /**
     * Add a subtask to a job (e.g., "Mine 64 oak_log").
     */
    public void addSubtask(String jobId, String subtaskType, String target, int quantity) {
        MultiTaskJob job = activeJobs.get(jobId);
        if (job == null) {
            LOGGER.warn("Job not found: {}", jobId);
            return;
        }

        SubTask subtask = new SubTask(subtaskType, target, quantity);
        job.subtasks.add(subtask);
        LOGGER.info("Added subtask to job {}: {}", jobId, subtask);
    }

    /**
     * Assign subtasks to available bots based on job type and available workers.
     */
    public void dispatchSubtasks(String jobId, MinecraftServer server) {
        MultiTaskJob job = activeJobs.get(jobId);
        if (job == null) {
            LOGGER.warn("Job not found: {}", jobId);
            return;
        }

        List<BotRegistry.BotInstance> workers = botRegistry.getBotsByOwner(job.owner);
        if (workers.isEmpty()) {
            LOGGER.warn("No available workers for job {} by owner {}", jobId, job.owner);
            return;
        }

        int botIndex = 0;
        for (SubTask subtask : job.subtasks) {
            if (subtask.status != SubTaskStatus.PENDING) continue;

            // Round-robin assign subtasks to available bots
            BotRegistry.BotInstance targetBot = workers.get(botIndex % workers.size());

            // Create task from subtask
            TaskManager.Task task = new TaskManager.Task(
                "sub_" + subtask.taskId,
                getCommandTypeForSubtask(subtask),
                TaskManager.TaskPriority.NORMAL,
                buildParametersFromSubtask(subtask)
            );

            // Queue task on target bot
            targetBot.taskManager.enqueueTask(task);

            // Track assignment
            SubTaskAssignment assignment = new SubTaskAssignment(subtask.taskId, targetBot.botName);
            job.assignments.put(subtask.taskId, assignment);
            subtask.status = SubTaskStatus.ASSIGNED;

            LOGGER.info("Assigned subtask {} to bot {}", subtask.taskId, targetBot.botName);
            botIndex++;
        }

        job.status = JobStatus.EXECUTING;
        LOGGER.info("Dispatched job {}: {} subtasks assigned", jobId, job.subtasks.size());
    }

    /**
     * Monitor job progress and update status.
     */
    public void tickJobs(MinecraftServer server) {
        for (MultiTaskJob job : new ArrayList<>(activeJobs.values())) {
            if (job.status != JobStatus.EXECUTING) continue;

            int completed = 0;
            int failed = 0;

            for (SubTask subtask : job.subtasks) {
                if (subtask.status == SubTaskStatus.COMPLETED) completed++;
                if (subtask.status == SubTaskStatus.FAILED) failed++;
            }

            if (completed == job.subtasks.size()) {
                job.status = JobStatus.COMPLETED;
                job.completedAt = System.currentTimeMillis();
                LOGGER.info("Job completed: {}", job.jobId);
            } else if (failed > 0 && (completed + failed) == job.subtasks.size()) {
                job.status = JobStatus.FAILED;
                LOGGER.warn("Job failed (some subtasks failed): {}", job.jobId);
            }
        }
    }

    /**
     * Pause all bots working on a job.
     */
    public void pauseJob(String jobId, String requesterName) {
        MultiTaskJob job = activeJobs.get(jobId);
        if (job == null || !job.owner.equalsIgnoreCase(requesterName)) {
            LOGGER.warn("Cannot pause job {}: not found or permission denied", jobId);
            return;
        }

        for (SubTaskAssignment assignment : job.assignments.values()) {
            BotRegistry.BotInstance bot = botRegistry.getBot(assignment.assignedBot);
            if (bot != null) {
                bot.taskManager.pauseCurrentTask();
            }
        }

        job.status = JobStatus.PAUSED;
        LOGGER.info("Job paused: {}", jobId);
    }

    /**
     * Resume a paused job.
     */
    public void resumeJob(String jobId, String requesterName) {
        MultiTaskJob job = activeJobs.get(jobId);
        if (job == null || !job.owner.equalsIgnoreCase(requesterName)) {
            LOGGER.warn("Cannot resume job {}: not found or permission denied", jobId);
            return;
        }

        for (SubTaskAssignment assignment : job.assignments.values()) {
            BotRegistry.BotInstance bot = botRegistry.getBot(assignment.assignedBot);
            if (bot != null) {
                bot.taskManager.resumeCurrentTask();
            }
        }

        job.status = JobStatus.EXECUTING;
        LOGGER.info("Job resumed: {}", jobId);
    }

    /**
     * Cancel a job and recall all bots.
     */
    public void cancelJob(String jobId, String requesterName) {
        MultiTaskJob job = activeJobs.get(jobId);
        if (job == null || !job.owner.equalsIgnoreCase(requesterName)) {
            LOGGER.warn("Cannot cancel job {}: not found or permission denied", jobId);
            return;
        }

        for (SubTaskAssignment assignment : job.assignments.values()) {
            BotRegistry.BotInstance bot = botRegistry.getBot(assignment.assignedBot);
            if (bot != null) {
                bot.taskManager.cancelCurrentTask("Job cancelled by owner");
            }
        }

        job.status = JobStatus.CANCELLED;
        LOGGER.info("Job cancelled: {}", jobId);
    }

    /**
     * Get job details.
     */
    public MultiTaskJob getJob(String jobId) {
        return activeJobs.get(jobId);
    }

    /**
     * Get all jobs.
     */
    public Collection<MultiTaskJob> getAllJobs() {
        return new ArrayList<>(activeJobs.values());
    }

    // ---- Helpers for subtask -> task conversion ----

    private AICommandParser.CommandType getCommandTypeForSubtask(SubTask subtask) {
        switch (subtask.type.toLowerCase()) {
            case "mine" -> { return AICommandParser.CommandType.TASK_MINE_IRON; }  // Generic mine
            case "gather" -> { return AICommandParser.CommandType.TASK_RAID_FARM; }  // Generic gather
            case "build" -> { return AICommandParser.CommandType.TASK_BUILD; }  // Custom: not standard
            case "guard" -> { return AICommandParser.CommandType.PVP_DEFEND; }
            default -> { return AICommandParser.CommandType.TASK_AFK; }
        }
    }

    private Map<String, String> buildParametersFromSubtask(SubTask subtask) {
        Map<String, String> params = new HashMap<>();
        params.put("type", subtask.type);
        params.put("target", subtask.target);
        params.put("amount", String.valueOf(subtask.quantity));
        return params;
    }
}
