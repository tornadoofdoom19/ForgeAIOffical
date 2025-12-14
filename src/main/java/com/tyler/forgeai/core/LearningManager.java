package com.tyler.forgeai.core;

import com.tyler.forgeai.ai.LearningStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.regex.Pattern;

/**
 * LearningManager: Handles learning custom instructions and procedures from player input.
 * Stores and retrieves learned behaviors for automation.
 */
public class LearningManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-learning-manager");

    private final LearningStore store;
    private final Map<String, LearnedProcedure> learnedProcedures = new HashMap<>();

    public static class LearnedProcedure {
        public String name;
        public String description;
        public List<String> steps;
        public long learnedAt;
        public String taughtBy;
        public int successCount = 0;
        public int failureCount = 0;

        public LearnedProcedure(String name, String description, List<String> steps, String taughtBy) {
            this.name = name;
            this.description = description;
            this.steps = new ArrayList<>(steps);
            this.taughtBy = taughtBy;
            this.learnedAt = System.currentTimeMillis();
        }

        public void recordSuccess() {
            successCount++;
        }

        public void recordFailure() {
            failureCount++;
        }

        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 0.0;
        }

        @Override
        public String toString() {
            return String.format("Procedure '%s': %s (Success rate: %.2f%%, %d attempts)",
                name, description, getSuccessRate() * 100, successCount + failureCount);
        }
    }

    public LearningManager(LearningStore store) {
        this.store = store;
        loadLearnedProcedures();
        LOGGER.info("LearningManager initialized with {} learned procedures", learnedProcedures.size());
    }

    /**
     * Learn a new procedure from player instruction.
     */
    public boolean learnProcedure(String instruction) {
        // Parse instruction in format: "learn <name>: <description> - <step1>, <step2>, ..."
        Pattern pattern = Pattern.compile("learn\\s+([^:]+):\\s*([^\\-]+)\\s*-\\s*(.+)", Pattern.CASE_INSENSITIVE);
        var matcher = pattern.matcher(instruction.trim());

        if (matcher.matches()) {
            String name = matcher.group(1).trim();
            String description = matcher.group(2).trim();
            String stepsStr = matcher.group(3).trim();

            List<String> steps = Arrays.asList(stepsStr.split("\\s*,\\s*"));

            LearnedProcedure procedure = new LearnedProcedure(name, description, steps, "player");
            learnedProcedures.put(name.toLowerCase(), procedure);

            // Store in learning store
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("description", description);
            data.put("steps", steps);
            data.put("taughtBy", "player");
            data.put("learnedAt", procedure.learnedAt);
            store.record("learned_procedure_" + name.toLowerCase(), data);

            LOGGER.info("Learned new procedure: {}", name);
            return true;
        }

        return false;
    }

    /**
     * Execute a learned procedure.
     */
    public boolean executeProcedure(String procedureName, DecisionEngine decisionEngine) {
        LearnedProcedure procedure = learnedProcedures.get(procedureName.toLowerCase());
        if (procedure == null) {
            LOGGER.warn("Unknown procedure: {}", procedureName);
            return false;
        }

        LOGGER.info("Executing learned procedure: {}", procedureName);

        try {
            // Convert steps to tasks and queue them
            for (String step : procedure.steps) {
                // Parse step as a command
                var parsedCommand = com.tyler.forgeai.api.PromptParser.parsePrompt(step);
                if (parsedCommand != null && decisionEngine.getTaskManager() != null) {
                    TaskManager.TaskPriority priority = TaskManager.getPriorityForCommand(parsedCommand.type);
                    decisionEngine.getTaskManager().queueTask(parsedCommand, priority);
                }
            }

            procedure.recordSuccess();
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to execute procedure {}: {}", procedureName, e.getMessage());
            procedure.recordFailure();
            return false;
        }
    }

    /**
     * Get a learned procedure by name.
     */
    public LearnedProcedure getProcedure(String name) {
        return learnedProcedures.get(name.toLowerCase());
    }

    /**
     * List all learned procedures.
     */
    public List<LearnedProcedure> getAllProcedures() {
        return new ArrayList<>(learnedProcedures.values());
    }

    /**
     * Forget a learned procedure.
     */
    public boolean forgetProcedure(String name) {
        LearnedProcedure removed = learnedProcedures.remove(name.toLowerCase());
        if (removed != null) {
            // Remove from learning store
            store.forget("learned_procedure_" + name.toLowerCase());
            LOGGER.info("Forgot procedure: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Learn from an observation (used for reinforcement learning).
     */
    public void learnFromObservation(String observationType, String context, boolean success) {
        Map<String, Object> observation = new HashMap<>();
        observation.put("type", observationType);
        observation.put("context", context);
        observation.put("success", success);
        observation.put("timestamp", System.currentTimeMillis());
        
        store.record("observation_" + observationType + "_" + context, observation);
        LOGGER.debug("Recorded observation: {} - {} - {}", observationType, context, success);
    }

    /**
     * Load learned procedures from the learning store.
     */
    @SuppressWarnings("unchecked")
    private void loadLearnedProcedures() {
        try {
            List<Map<String, Object>> procedures = store.getObservations("learned_procedure");
            for (Map<String, Object> data : procedures) {
                String name = (String) data.get("name");
                String description = (String) data.get("description");
                List<String> steps = (List<String>) data.get("steps");
                String taughtBy = (String) data.get("taughtBy");

                if (name != null && description != null && steps != null) {
                    LearnedProcedure procedure = new LearnedProcedure(name, description, steps, taughtBy);
                    learnedProcedures.put(name.toLowerCase(), procedure);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error loading learned procedures: {}", e.getMessage());
        }
    }

    /**
     * Learn a location-based procedure (e.g., how to find a Nether portal, bastion, etc.)
     */
    public boolean learnLocationProcedure(String instruction, com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        // Parse instruction like: "learn location: Nether Portal - go to overworld, find obsidian structure"
        Pattern pattern = Pattern.compile("learn location:\\s*([^\\-]+)\\s*-\\s*(.+)", Pattern.CASE_INSENSITIVE);
        var matcher = pattern.matcher(instruction.trim());

        if (matcher.matches()) {
            String locationName = matcher.group(1).trim();
            String stepsStr = matcher.group(2).trim();

            List<String> steps = Arrays.asList(stepsStr.split("\\s*,\\s*"));

            LearnedProcedure procedure = new LearnedProcedure("find_" + locationName.toLowerCase().replace(" ", "_"),
                "How to find " + locationName, steps, "player");
            learnedProcedures.put(procedure.name, procedure);

            // Store in learning store
            Map<String, Object> data = new HashMap<>();
            data.put("name", procedure.name);
            data.put("description", procedure.description);
            data.put("steps", steps);
            data.put("taughtBy", "player");
            data.put("learnedAt", procedure.learnedAt);
            data.put("locationType", "learned_location");
            store.record("learned_location_" + procedure.name, data);

            LOGGER.info("Learned location procedure: {}", locationName);
            return true;
        }

        return false;
    }

    /**
     * Execute a task to find and collect a specific item.
     */
    public boolean executeFindItemTask(String itemName, DecisionEngine decisionEngine) {
        LOGGER.info("Executing find item task: {}", itemName);

        try {
            // Check if we have a learned procedure for finding this item
            LearnedProcedure procedure = learnedProcedures.get("find_" + itemName.toLowerCase().replace(" ", "_"));
            if (procedure != null) {
                return executeProcedure(procedure.name, decisionEngine);
            }

            // Otherwise, use general item finding logic
            // This would be handled by the gatherer module
            var gathererModule = decisionEngine.getGathererModule();
            if (gathererModule != null) {
                // Queue a task to collect the specific item
                Map<String, String> params = new HashMap<>();
                params.put("item", itemName);
                var command = new AICommandParser.ParsedCommand(
                    AICommandParser.CommandType.TASK_FIND_ITEM,
                    "find " + itemName,
                    params
                );
                TaskManager.TaskPriority priority = TaskManager.getPriorityForCommand(command.type);
                decisionEngine.getTaskManager().queueTask(command, priority);
                return true;
            }

        } catch (Exception e) {
            LOGGER.error("Failed to execute find item task {}: {}", itemName, e.getMessage());
        }

        return false;
    }

    /**
     * Execute a task to go to a remembered location.
     */
    public boolean executeGoToLocationTask(String locationName, DecisionEngine decisionEngine) {
        LOGGER.info("Executing go to location task: {}", locationName);

        try {
            var worldMemory = decisionEngine.getSharedWorldMemory();
            if (worldMemory != null) {
                // Try to find the location in memory
                var location = worldMemory.getLocation(locationName);
                if (location != null) {
                    // Navigate to the location
                    Map<String, String> params = new HashMap<>();
                    params.put("x", String.valueOf(location.x));
                    params.put("y", String.valueOf(location.y));
                    params.put("z", String.valueOf(location.z));
                    params.put("dimension", location.dimension);
                    var command = new AICommandParser.ParsedCommand(
                        AICommandParser.CommandType.NAV_GO_TO,
                        "go to " + locationName,
                        params
                    );
                    TaskManager.TaskPriority priority = TaskManager.getPriorityForCommand(command.type);
                    decisionEngine.getTaskManager().queueTask(command, priority);
                    return true;
                } else {
                    // Location not found, try to learn/find it
                    LOGGER.info("Location {} not in memory, attempting to find it", locationName);
                    return executeFindLocationTask(locationName, decisionEngine);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to execute go to location task {}: {}", locationName, e.getMessage());
        }

        return false;
    }

    /**
     * Execute a task to find a location (explore until found).
     */
    public boolean executeFindLocationTask(String locationName, DecisionEngine decisionEngine) {
        LOGGER.info("Executing find location task: {}", locationName);

        try {
            // Check if we have a learned procedure for finding this location
            LearnedProcedure procedure = learnedProcedures.get("find_" + locationName.toLowerCase().replace(" ", "_"));
            if (procedure != null) {
                return executeProcedure(procedure.name, decisionEngine);
            }

            // Otherwise, use exploration to find it
            Map<String, String> params = new HashMap<>();
            params.put("target", locationName);
            var command = new AICommandParser.ParsedCommand(
                AICommandParser.CommandType.TASK_FIND_STRUCTURE,
                "find " + locationName,
                params
            );
            TaskManager.TaskPriority priority = TaskManager.getPriorityForCommand(command.type);
            decisionEngine.getTaskManager().queueTask(command, priority);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to execute find location task {}: {}", locationName, e.getMessage());
        }

        return false;
    }

    /**
     * Get suggestions based on learned behaviors.
     */
    public List<String> getSuggestions(String context) {
        List<String> suggestions = new ArrayList<>();

        // Look for similar contexts in learned procedures
        for (LearnedProcedure procedure : learnedProcedures.values()) {
            if (procedure.description.toLowerCase().contains(context.toLowerCase())) {
                suggestions.add(procedure.name);
            }
        }

        return suggestions;
    }
}
