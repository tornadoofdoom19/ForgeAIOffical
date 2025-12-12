package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Food utilities: consume food when health is low to maintain survival.
 * Prioritizes high-saturation foods and always uses available inventory items.
 */
public final class FoodUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-food");

    private FoodUtils() {}

    /**
     * Consume available food if player health is below threshold.
     * Returns true if food was consumed, false otherwise.
     */
    public static boolean autoEatIfLow(ServerPlayer player, float healthThreshold) {
        if (player == null) return false;
        
        float currentHealth = player.getHealth();
        if (currentHealth >= healthThreshold) return false; // Not hungry enough
        
        try {
            var inv = player.getInventory();
            ItemStack foodItem = findBestFood(inv);
            if (foodItem == null || foodItem.isEmpty()) {
                LOGGER.debug("No food available to consume (health={})", currentHealth);
                return false;
            }

            // Attempt to consume: move to hotbar, select, and use
            String foodName = foodItem.getItem().toString();
            boolean moved = InventoryUtils.moveItemToHotbar(player, foodName);
            if (!moved) {
                LOGGER.debug("Could not move food {} to hotbar", foodName);
                return false;
            }

            // Attempt to use the selected hotbar slot to eat
            try {
                var inv = player.getInventory();
                int sel = inv.selected;
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, inv.getItem(sel));
                com.tyler.forgeai.util.PlayerActionUtils.useMainHand(player, 32);
                LOGGER.info("Player health low ({}); consuming food item: {}", currentHealth, foodName);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error triggering eat action: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Auto-eat failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Find the best food item in inventory by saturation/healing value.
     * Prioritizes golden apples, suspicious stew, cooked foods, then raw foods.
     */
    private static ItemStack findBestFood(net.minecraft.world.entity.player.Inventory inv) {
        ItemStack best = null;
        int bestScore = -1;

        try {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack == null || stack.isEmpty()) continue;
                
                String name = stack.getItem().toString().toLowerCase();
                int score = scoreFoodQuality(name);
                
                if (score > bestScore) {
                    bestScore = score;
                    best = stack;
                }
            }
        } catch (Exception ignored) {}

        return best;
    }

    /**
     * Score food items by healing/saturation value (higher = better).
     * Used to prioritize high-efficiency foods.
     */
    private static int scoreFoodQuality(String itemName) {
        // Tier 1: Excellent (10+ saturation, healing)
        if (itemName.contains("golden_apple")) return 100;
        if (itemName.contains("enchanted_golden_apple")) return 110;
        if (itemName.contains("suspicious_stew")) return 90;
        
        // Tier 2: Good (7-9 saturation)
        if (itemName.contains("cooked_beef")) return 80;
        if (itemName.contains("cooked_pork")) return 75;
        if (itemName.contains("cooked_chicken")) return 70;
        if (itemName.contains("cooked_mutton")) return 75;
        if (itemName.contains("cooked_salmon")) return 80;
        if (itemName.contains("cooked_cod")) return 70;
        if (itemName.contains("baked_potato")) return 60;
        if (itemName.contains("bread")) return 60;
        
        // Tier 3: Okay (4-6 saturation)
        if (itemName.contains("apple")) return 45;
        if (itemName.contains("carrot")) return 35;
        if (itemName.contains("potato")) return 30;
        if (itemName.contains("beef")) return 35;
        if (itemName.contains("chicken")) return 30;
        if (itemName.contains("pork")) return 30;
        if (itemName.contains("mutton")) return 30;
        if (itemName.contains("salmon")) return 35;
        if (itemName.contains("cod")) return 30;
        if (itemName.contains("honeycomb")) return 20;
        
        return 0; // Not food
    }
}
