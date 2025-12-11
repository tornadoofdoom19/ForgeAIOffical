package com.tyler.forgeai.modules.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ResourceTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-resources");

    // Track resource counts by item name
    private final Map<String, Integer> resourceCounts = new HashMap<>();

    public void init() {
        LOGGER.info("ResourceTracker initialized.");
    }

    /**
     * Update resource counts from player inventory.
     */
    public void update(Object player) {
        resourceCounts.clear();
        // TODO: Implement with proper ServerPlayer reflection or casting
        LOGGER.debug("Resource counts updated: " + resourceCounts);
    }

    /**
     * Get count of a specific resource.
     */
    public int getCount(String itemName) {
        return resourceCounts.getOrDefault(itemName.toLowerCase(), 0);
    }

    /**
     * Check if resource is below threshold.
     */
    public boolean isLow(String itemName, int threshold) {
        return getCount(itemName) < threshold;
    }

    /**
     * Suggest gathering action if resource is low.
     */
    public boolean needsGathering(String itemName, int threshold) {
        boolean low = isLow(itemName, threshold);
        if (low) {
            LOGGER.info("Resource low: " + itemName + " below " + threshold);
        }
        return low;
    }

    /**
     * Dump all tracked resources.
     */
    public Map<String, Integer> getAllResources() {
        return new HashMap<>(resourceCounts);
    }
}
