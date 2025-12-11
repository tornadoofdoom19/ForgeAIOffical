package com.tyler.forgeai.util;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class InventoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-inventory");

    public void init() {
        LOGGER.info("InventoryManager initialized.");
    }

    /**
     * Check if player has a specific item in inventory.
     */
    public boolean hasItem(ServerPlayerEntity player, String itemName) {
        return player.getInventory().main.stream()
            .anyMatch(stack -> stack != null && stack.getItem().toString().toLowerCase().contains(itemName.toLowerCase()));
    }

    /**
     * Get the first stack of a specific item.
     */
    public Optional<ItemStack> getItem(ServerPlayerEntity player, String itemName) {
        return player.getInventory().main.stream()
            .filter(stack -> stack != null && stack.getItem().toString().toLowerCase().contains(itemName.toLowerCase()))
            .findFirst();
    }

    /**
     * Equip item into main hand if available.
     */
    public void equipItem(ServerPlayerEntity player, String itemName) {
        Optional<ItemStack> stackOpt = getItem(player, itemName);
        if (stackOpt.isPresent()) {
            player.getInventory().selectedSlot = player.getInventory().main.indexOf(stackOpt.get());
            LOGGER.info("Equipped " + itemName + " in main hand for " + player.getName().getString());
        } else {
            LOGGER.debug("Item " + itemName + " not found in inventory for " + player.getName().getString());
        }
    }

    /**
     * Count how many of a specific item the player has.
     */
    public int countItem(ServerPlayerEntity player, String itemName) {
        return (int) player.getInventory().main.stream()
            .filter(stack -> stack != null && stack.getItem().toString().toLowerCase().contains(itemName.toLowerCase()))
            .count();
    }

    /**
     * Check if inventory is full.
     */
    public boolean isInventoryFull(ServerPlayerEntity player) {
        return player.getInventory().main.stream().allMatch(stack -> stack != null && !stack.isEmpty());
    }
}
