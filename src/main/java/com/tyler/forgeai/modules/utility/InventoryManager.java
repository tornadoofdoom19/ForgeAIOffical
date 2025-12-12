package com.tyler.forgeai.modules.utility;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import com.tyler.forgeai.util.InventoryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tyler.forgeai.ai.LearningStore;
import java.util.*;

import java.util.Optional;

public class InventoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-inventory");

    public void init() {
        LOGGER.info("InventoryManager initialized.");
    }

    private LearningStore learningStore = null;

    public void attachLearningStore(LearningStore ls) { this.learningStore = ls; }

    /**
     * Check if player has a specific item in inventory.
     */
    public boolean hasItem(Object player, String itemName) {
        try {
            if (player instanceof ServerPlayer sp) {
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        String name = stack.getItem().toString().toLowerCase();
                        if (name.contains(itemName.toLowerCase())) return true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("hasItem error: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get the first stack of a specific item.
     */
    public Optional<ItemStack> getItem(Object player, String itemName) {
        try {
            if (player instanceof ServerPlayer sp) {
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        String name = stack.getItem().toString().toLowerCase();
                        if (name.contains(itemName.toLowerCase())) return Optional.of(stack);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("getItem error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Equip item into main hand if available.
     */
    public void equipItem(Object player, String itemName) {
        try {
            if (player instanceof ServerPlayer sp) {
                var moved = InventoryUtils.moveItemToHotbar(sp, itemName);
                if (moved) {
                    LOGGER.info("Equipped item into hotbar: {}", itemName);
                } else {
                    LOGGER.info("Could not equip item: {}", itemName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("equipItem failed: {}", e.getMessage());
        }
    }

    /**
     * Count how many of a specific item the player has.
     */
    public int countItem(Object player, String itemName) {
        int count = 0;
        try {
            if (player instanceof ServerPlayer sp) {
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    if (stack != null && !stack.isEmpty()) {
                        String name = stack.getItem().toString().toLowerCase();
                        if (name.contains(itemName.toLowerCase())) count += stack.getCount();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("countItem error: {}", e.getMessage());
        }
        return count;
    }

    /**
     * Suggest an optimized hotbar layout for a given mode (pvp, build, travel)
     */
    public List<String> suggestHotbarLayout(Object player, String mode) {
        List<String> defaultLayout = new ArrayList<>();
        if ("pvp".equalsIgnoreCase(mode)) {
            defaultLayout = List.of("sword", "shield", "totem", "gap", "pearl", "axe", "bow", "food");
        } else if ("build".equalsIgnoreCase(mode)) {
            defaultLayout = List.of("pickaxe", "axe", "shovel", "planks", "torches", "shears", "bucket", "food");
        } else if ("travel".equalsIgnoreCase(mode)) {
            defaultLayout = List.of("elytra", "riptide_trident", "boat", "blocks", "food", "torch", "saddle", "bow");
        } else {
            defaultLayout = List.of("sword", "pickaxe", "axe", "shovel", "blocks", "bow", "food", "torch");
        }
        try {
            // If we have learning store, try to get previous successful layout
            if (learningStore != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                var obs = learningStore.getObservations("hotbar-" + sp.getGameProfile().getName());
                if (!obs.isEmpty()) {
                    var last = obs.get(obs.size() - 1);
                    Object candidate = last.get("mode");
                    if (candidate != null && mode.equalsIgnoreCase(candidate.toString())) {
                        Object layout = last.get("layout");
                        if (layout instanceof List) {
                            List<String> l = new ArrayList<>();
                            for (Object o : (List<?>) layout) l.add(o.toString());
                            return l;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return defaultLayout;
    }

    /**
     * Record a hotbar layout for this player and mode in LearningStore.
     */
    public void recordHotbarLayout(Object player, String mode, List<String> layout) {
        if (learningStore == null || layout == null) return;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        Map<String, Object> obs = new HashMap<>();
        obs.put("mode", mode);
        obs.put("layout", layout);
        learningStore.record("hotbar-" + sp.getGameProfile().getName(), obs);
    }

    /**
     * Check if inventory is full.
     */
    public boolean isInventoryFull(Object player) {
        try {
            if (player instanceof ServerPlayer sp) {
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    ItemStack stack = sp.getInventory().getItem(i);
                    if (stack == null || stack.isEmpty()) return false;
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("isInventoryFull error: {}", e.getMessage());
        }
        return false;
    }
}
