package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory utilities (best-effort): move items into hotbar and choose active slot.
 * These implementations are intentionally conservative and log actions. They may be
 * replaced with game-specific mappings if your environment uses different mappings.
 */
public final class InventoryUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-inventory");

    private InventoryUtils() {}

    /**
     * Attempt to move the first inventory item whose name contains `keyword` into the hotbar.
     * Returns true if the item was already in the hotbar or was moved successfully.
     */
    public static boolean moveItemToHotbar(ServerPlayer player, String keyword) {
        if (player == null || keyword == null) return false;
        try {
            // Try quick checks using inventory lists. This is best-effort and may not work
            // in all mappings; callers should handle false returns gracefully.
            var inv = player.getInventory();
            // Search full inventory for matching item
            int foundIndex = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != null && !stack.isEmpty()) {
                    String name = stack.getItem().toString().toLowerCase();
                    if (name.contains(keyword.toLowerCase())) { foundIndex = i; break; }
                }
            }
            if (foundIndex == -1) return false;

            // If already in hotbar (0-8), select it
            if (foundIndex >= 0 && foundIndex <= 8) {
                inv.selected = foundIndex; // best-effort: set selected slot
                LOGGER.debug("Item with keyword '{}' already in hotbar slot {}", keyword, foundIndex);
                return true;
            }

            // Find empty hotbar slot or a non-reserved slot (avoid evicting weapons/food)
            int target = -1;
            String[] reserved = new String[]{"sword","axe","pickaxe","shovel","hoe","bow","crossbow","shield","apple","bread","cooked","chicken","pork","beef","mutton","potato","carrot","honey","golden_apple","suspicious_stew"};
            for (int h = 0; h <= 8; h++) {
                ItemStack hs = inv.getItem(h);
                if (hs == null || hs.isEmpty()) { target = h; break; }
            }
            if (target == -1) {
                // Prefer current selected slot if it is not reserved
                try {
                    ItemStack sel = inv.getItem(inv.selected);
                    boolean selReserved = false;
                    if (sel != null && !sel.isEmpty()) {
                        String sname = sel.getItem().toString().toLowerCase();
                        for (String r : reserved) if (sname.contains(r)) { selReserved = true; break; }
                    }
                    if (!selReserved) target = inv.selected;
                } catch (Exception ignored) {}
            }
            if (target == -1) {
                // Find a hotbar slot that does not contain reserved item
                for (int h = 0; h <= 8; h++) {
                    try {
                        ItemStack hs = inv.getItem(h);
                        if (hs == null || hs.isEmpty()) { target = h; break; }
                        String name = hs.getItem().toString().toLowerCase();
                        boolean reservedSlot = false;
                        for (String r : reserved) if (name.contains(r)) { reservedSlot = true; break; }
                        if (!reservedSlot) { target = h; break; }
                    } catch (Exception ignored) {}
                }
            }
            if (target == -1) target = inv.selected >= 0 ? inv.selected : 0;

            // Swap foundIndex <-> target
            try {
                ItemStack tmp = inv.getItem(target) == null ? ItemStack.EMPTY : inv.getItem(target).copy();
                ItemStack found = inv.getItem(foundIndex) == null ? ItemStack.EMPTY : inv.getItem(foundIndex).copy();
                inv.setItem(target, found);
                inv.setItem(foundIndex, tmp);
                inv.selected = target;
                LOGGER.info("Moved item '{}' into hotbar slot {} (from {})", keyword, target, foundIndex);
            } catch (Exception e) {
                LOGGER.debug("Hotbar swap failed: {}", e.getMessage());
                return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.debug("Failed to move item to hotbar: {}", e.getMessage());
            return false;
        }
    }
}
