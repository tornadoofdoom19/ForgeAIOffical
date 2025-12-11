package com.tyler.forgeai.ai;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MemoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-memory");

    // Simple in‑memory store of experiences
    private final List<Experience> experiences = new ArrayList<>();

    public void init() {
        LOGGER.info("MemoryManager initialized.");
    }

    /**
     * Record a new experience.
     */
    public void recordExperience(String moduleName, Signals context, boolean success) {
        Experience exp = new Experience(moduleName, context, success, System.currentTimeMillis());
        experiences.add(exp);
        LOGGER.debug("Recorded experience: " + exp);
    }

    /**
     * Retrieve past experiences for a module.
     */
    public List<Experience> getExperiences(String moduleName) {
        List<Experience> result = new ArrayList<>();
        for (Experience e : experiences) {
            if (e.moduleName.equalsIgnoreCase(moduleName)) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Get last N experiences for quick recall.
     */
    public List<Experience> getRecentExperiences(int count) {
        int start = Math.max(0, experiences.size() - count);
        return experiences.subList(start, experiences.size());
    }

    /**
     * Clear memory (for resets or retraining).
     */
    public void clearMemory() {
        experiences.clear();
        LOGGER.info("Memory cleared.");
    }

    // Inner class representing an experience
    public static class Experience {
        public final String moduleName;
        public final Signals context;
        public final boolean success;
        public final long timestamp;

        public Experience(String moduleName, Signals context, boolean success, long timestamp) {
            this.moduleName = moduleName;
            this.context = context;
            this.success = success;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "Experience{" +
                    "module='" + moduleName + '\'' +
                    ", success=" + success +
                    ", time=" + new Date(timestamp) +
                    '}';
        }
    }
}
