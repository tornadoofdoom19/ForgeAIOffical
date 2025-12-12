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
        try {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                var inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    net.minecraft.world.item.ItemStack stack = inv.getItem(i);
                    if (stack == null || stack.isEmpty()) continue;
                    String name = stack.getItem().toString().toLowerCase();
                    resourceCounts.put(name, resourceCounts.getOrDefault(name, 0) + stack.getCount());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Resource update failed: {}", e.getMessage());
        }
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
