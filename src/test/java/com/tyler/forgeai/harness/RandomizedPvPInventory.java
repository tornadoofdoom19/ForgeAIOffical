package com.tyler.forgeai.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates randomized PvP inventory for testing.
 * - Includes PvP items with randomized durability and counts
 * - Randomizes active potion effects
 * - Provides detailed inventory state for DecisionEngine evaluation
 */
public class RandomizedPvPInventory {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-inventory");

    // PvP item types
    public enum PvPItem {
        SWORD("Sword", 0.5, 1, 384),          // Common, 1x in most scenarios, max durability 384
        MACE("Mace", 0.4, 1, 384),            // Common, 1x, max durability 384
        SHIELD("Shield", 0.5, 1, 336),        // Essential survival, 1x, max durability 336
        CRYSTAL("End Crystal", 0.3, 0, 1),    // Consumable, variable count, no durability
        CART("Minecart", 0.2, 0, 1),          // Utility, variable count
        WEBS("Webs", 0.4, 0, 64),             // Consumable, variable count
        WIND_CHARGE("Wind Charge", 0.3, 0, 64),// Consumable, variable count
        WATER_BUCKET("Water Bucket", 0.3, 0, 1),// Survival utility, 1 primary
        BOW("Bow", 0.2, 1, 384),              // Utility ranged, 1x, max durability
        FISHING_ROD("Fishing Rod", 0.2, 1, 64),// Utility, 1x, max durability
        ENDER_PEARL("Ender Pearl", 0.3, 0, 64),// Consumable mobility, variable count
        ELYTRA("Elytra", 0.2, 0, 432),        // Rare mobility, 0-1, max durability
        TOTEM("Totem of Undying", 0.4, 0, 64),// Critical survival, variable count
        ARROWS("Arrows", 0.2, 0, 64),         // Consumable, variable count
        POTIONS("Potions", 0.3, 0, 64);       // Consumable, variable count

        private final String displayName;
        private final double spawnChance;
        private final int minCount;
        private final int maxDurability;

        PvPItem(String displayName, double spawnChance, int minCount, int maxDurability) {
            this.displayName = displayName;
            this.spawnChance = spawnChance;
            this.minCount = minCount;
            this.maxDurability = maxDurability;
        }

        public String getDisplayName() { return displayName; }
        public double getSpawnChance() { return spawnChance; }
        public int getMinCount() { return minCount; }
        public int getMaxDurability() { return maxDurability; }
    }

    // Potion effect types
    public enum PotionEffect {
        STRENGTH("Strength", 0.6),
        SPEED("Speed", 0.7),
        REGENERATION("Regeneration", 0.5),
        FIRE_RESISTANCE("Fire Resistance", 0.3),
        RESISTANCE("Resistance", 0.4),
        NIGHT_VISION("Night Vision", 0.2),
        HASTE("Haste", 0.3),
        JUMP_BOOST("Jump Boost", 0.4);

        private final String displayName;
        private final double activationChance;

        PotionEffect(String displayName, double activationChance) {
            this.displayName = displayName;
            this.activationChance = activationChance;
        }

        public String getDisplayName() { return displayName; }
        public double getActivationChance() { return activationChance; }
    }

    // Inventory state
    private final Random random;
    private final Map<PvPItem, InventorySlot> inventory = new HashMap<>();
    private final Set<PotionEffect> activePotions = new HashSet<>();
    private float playerHealth = 20.0f;
    private int foodLevel = 20;
    private float[] armorDurability = {0.8f, 0.8f, 0.8f, 0.8f}; // Helmet, Chest, Legs, Boots

    public RandomizedPvPInventory() {
        this.random = new Random();
    }

    public RandomizedPvPInventory(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generate randomized inventory.
     */
    public void randomize() {
        inventory.clear();
        activePotions.clear();

        // Generate items based on spawn chance
        for (PvPItem item : PvPItem.values()) {
            if (random.nextDouble() < item.getSpawnChance()) {
                int count = item.getMinCount() + random.nextInt(Math.max(1, item.getMaxDurability() - item.getMinCount() + 1));
                float durabilityPercent = 0.5f + (random.nextFloat() * 0.5f); // 50-100% durability
                inventory.put(item, new InventorySlot(item, count, durabilityPercent));
            }
        }

        // Ensure critical items are always present
        ensureItemPresence(PvPItem.SHIELD, 1);
        ensureItemPresence(PvPItem.TOTEM, random.nextInt(3) + 1); // 1-3 totems
        ensureItemPresence(PvPItem.SWORD, 1);

        // Randomize player health (10-20 hp = 5-10 hearts)
        playerHealth = 10.0f + (random.nextFloat() * 10.0f);

        // Randomize food level
        foodLevel = 10 + random.nextInt(11); // 10-20

        // Randomize armor durability
        for (int i = 0; i < armorDurability.length; i++) {
            armorDurability[i] = 0.3f + (random.nextFloat() * 0.7f); // 30-100%
        }

        // Randomize active potions
        for (PotionEffect effect : PotionEffect.values()) {
            if (random.nextDouble() < effect.getActivationChance()) {
                activePotions.add(effect);
            }
        }

        LOGGER.debug("Inventory randomized: {} items, {} active potions, health={}, food={}",
                inventory.size(), activePotions.size(), playerHealth, foodLevel);
    }

    /**
     * Ensure an item is present in inventory (or add it if missing).
     */
    private void ensureItemPresence(PvPItem item, int count) {
        if (!inventory.containsKey(item)) {
            float durability = 0.7f + (random.nextFloat() * 0.3f);
            inventory.put(item, new InventorySlot(item, count, durability));
        }
    }

    // ---- Getters ----

    public boolean hasItem(PvPItem item) {
        return inventory.containsKey(item) && inventory.get(item).count > 0;
    }

    public int getItemCount(PvPItem item) {
        return inventory.getOrDefault(item, new InventorySlot(item, 0, 0)).count;
    }

    public float getItemDurability(PvPItem item) {
        return inventory.getOrDefault(item, new InventorySlot(item, 0, 0)).durabilityPercent;
    }

    public boolean hasActivePotion(PotionEffect effect) {
        return activePotions.contains(effect);
    }

    public Set<PotionEffect> getActivePotions() {
        return new HashSet<>(activePotions);
    }

    public float getPlayerHealth() {
        return playerHealth;
    }

    public void setPlayerHealth(float health) {
        this.playerHealth = Math.max(0, Math.min(20, health));
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float[] getArmorDurability() {
        return armorDurability.clone();
    }

    public boolean isLowHealth() {
        return playerHealth < 6.0f; // < 3 hearts
    }

    public boolean isArmorBroken() {
        for (float dur : armorDurability) {
            if (dur < 0.2f) return true; // Any piece < 20%
        }
        return false;
    }

    public Map<PvPItem, InventorySlot> getInventory() {
        return new HashMap<>(inventory);
    }

    // ---- Inventory Slot ----

    public static class InventorySlot {
        public final PvPItem item;
        public int count;
        public float durabilityPercent;

        public InventorySlot(PvPItem item, int count, float durabilityPercent) {
            this.item = item;
            this.count = count;
            this.durabilityPercent = durabilityPercent;
        }

        @Override
        public String toString() {
            if (item.getMaxDurability() <= 1) {
                return String.format("%s: x%d", item.getDisplayName(), count);
            } else {
                return String.format("%s: x%d (%.0f%% durability)", 
                    item.getDisplayName(), count, durabilityPercent * 100);
            }
        }
    }

    // ---- Logging ----

    public String summarizeInventory() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Inventory Summary ===\n");
        sb.append(String.format("Health: %.1f/20 | Food: %d/20 | Armor Durability: %.0f%%\n",
                playerHealth, foodLevel, getAverageArmorDurability() * 100));

        sb.append("Items:\n");
        if (inventory.isEmpty()) {
            sb.append("  (empty)\n");
        } else {
            for (InventorySlot slot : inventory.values()) {
                sb.append("  - ").append(slot).append("\n");
            }
        }

        sb.append("Active Potions:\n");
        if (activePotions.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (PotionEffect effect : activePotions) {
                sb.append("  - ").append(effect.getDisplayName()).append("\n");
            }
        }

        return sb.toString();
    }

    private float getAverageArmorDurability() {
        float total = 0;
        for (float d : armorDurability) {
            total += d;
        }
        return total / armorDurability.length;
    }

    @Override
    public String toString() {
        return String.format("RandomizedPvPInventory{items=%d, potions=%d, health=%.1f, food=%d}",
                inventory.size(), activePotions.size(), playerHealth, foodLevel);
    }
}
