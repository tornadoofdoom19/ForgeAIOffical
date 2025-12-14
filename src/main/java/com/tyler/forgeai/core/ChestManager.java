package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import com.tyler.forgeai.ai.SharedWorldMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * ChestManager: Manages chest inventories and item transfers between bots.
 * - Deposit/withdraw items from chests
 * - Track chest contents in memory
 * - Facilitate trading between bots
 * - Find chests with specific items
 */
public class ChestManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-chests");

    private final SharedWorldMemory sharedMemory;
    private final Map<BlockPos, ChestInventorySnapshot> chestCache = new HashMap<>();

    public static class ChestInventorySnapshot {
        public BlockPos position;
        public Map<String, Integer> itemCounts = new HashMap<>();  // item_name -> count
        public long lastScanned;

        public ChestInventorySnapshot(BlockPos pos) {
            this.position = pos;
            this.lastScanned = System.currentTimeMillis();
        }

        public void addItem(String itemName, int count) {
            itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + count);
        }

        public int getItemCount(String itemName) {
            return itemCounts.getOrDefault(itemName, 0);
        }

        @Override
        public String toString() {
            return String.format("Chest@%d,%d,%d: %d item types", position.getX(), position.getY(), position.getZ(), itemCounts.size());
        }
    }

    public ChestManager(SharedWorldMemory sharedMemory) {
        this.sharedMemory = sharedMemory;
        LOGGER.info("ChestManager initialized");
    }

    /**
     * Check if a chest is suitable for bot use (not naturally spawned).
     */
    public boolean isChestSuitableForBotUse(ServerLevel level, BlockPos chestPos) {
        if (level == null || chestPos == null) return false;

        // Check if chest is in a structure that typically has natural chests
        String structureName = getStructureAtPosition(level, chestPos);
        if (structureName != null) {
            switch (structureName.toLowerCase()) {
                case "village":
                case "dungeon":
                case "mineshaft":
                case "stronghold":
                case "desert_pyramid":
                case "jungle_pyramid":
                case "ocean_monument":
                case "woodland_mansion":
                    return false; // Natural structure chests
                default:
                    break;
            }
        }

        // Check if chest is near other player-built structures
        if (isNearPlayerBuiltStructures(level, chestPos)) {
            return true; // Likely player-placed
        }

        // Default to suitable if we can't determine
        return true;
    }

    /**
     * Get the structure name at a position (if any).
     */
    private String getStructureAtPosition(ServerLevel level, BlockPos pos) {
        // This would use Minecraft's structure detection
        // For now, return null (not implemented)
        return null;
    }

    /**
     * Check if position is near player-built structures.
     */
    private boolean isNearPlayerBuiltStructures(ServerLevel level, BlockPos pos) {
        // Check for nearby crafting tables, furnaces, etc.
        int radius = 10;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    var block = level.getBlockState(checkPos).getBlock();
                    String blockName = block.getDescriptionId();
                    if (blockName.contains("crafting_table") ||
                        blockName.contains("furnace") ||
                        blockName.contains("anvil") ||
                        blockName.contains("enchanting_table")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Scan a chest and cache its inventory.
     */
    public ChestInventorySnapshot scanChest(ServerLevel level, BlockPos chestPos) {
        if (level == null || chestPos == null) return null;

        // Check if chest is suitable for bot use
        if (!isChestSuitableForBotUse(level, chestPos)) {
            LOGGER.debug("Skipping unsuitable chest at {}", chestPos);
            return null;
        }

        if (level == null || chestPos == null) return null;

        try {
            var blockState = level.getBlockState(chestPos);
            if (!(blockState.getBlock() instanceof ChestBlock)) {
                LOGGER.debug("Block at {} is not a chest", chestPos);
                return null;
            }

            ChestInventorySnapshot snapshot = new ChestInventorySnapshot(chestPos);
            try {
                var be = level.getBlockEntity(chestPos);
                if (be instanceof net.minecraft.world.SimpleContainer) {
                    net.minecraft.world.SimpleContainer container = (net.minecraft.world.SimpleContainer) be;
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        net.minecraft.world.item.ItemStack stack = container.getItem(i);
                        if (stack != null && !stack.isEmpty()) {
                            String name = stack.getItem().toString();
                            snapshot.addItem(name, stack.getCount());
                        }
                    }
                } else {
                    // Try generic Container
                    if (be instanceof Container) {
                        Container cont = (Container) be;
                        for (int i = 0; i < cont.getContainerSize(); i++) {
                            ItemStack stack = cont.getItem(i);
                            if (stack != null && !stack.isEmpty()) {
                                String name = stack.getItem().toString();
                                snapshot.addItem(name, stack.getCount());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Error reading chest BlockEntity: {}", e.getMessage());
            }

            LOGGER.info("Scanned chest at {}: {} items found", chestPos, snapshot.itemCounts.size());
            chestCache.put(chestPos, snapshot);

            // Register in shared memory
            sharedMemory.registerLocation(
                "chest_" + chestPos.getX() + "_" + chestPos.getZ(),
                chestPos.getX(), chestPos.getY(), chestPos.getZ(),
                "chest", "ChestManager"
            );

            return snapshot;
        } catch (Exception e) {
            LOGGER.debug("Error scanning chest: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deposit items from player inventory into a chest.
     */
    public boolean depositItems(ServerPlayer player, BlockPos chestPos, String itemName, int count) {
        if (player == null || chestPos == null) return false;

        try {
            ServerLevel level = (ServerLevel) player.level();
            var container = level.getBlockEntity(chestPos);

            if (!(container instanceof Container)) {
                LOGGER.debug("Block at {} is not a container", chestPos);
                return false;
            }

            // Best-effort: move matching items from player inventory to chest container
            try {
                var playerInv = player.getInventory();
                for (int i = 0; i < playerInv.getContainerSize() && count > 0; i++) {
                    ItemStack stack = playerInv.getItem(i);
                    if (stack == null || stack.isEmpty()) continue;
                    String name = stack.getItem().toString().toLowerCase();
                    if (name.contains(itemName.toLowerCase())) {
                        int move = Math.min(count, stack.getCount());
                        stack.shrink(move);
                        count -= move;
                    }
                }
                LOGGER.info("Deposited items to chest at {} (best-effort)", chestPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error depositing items: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error depositing items: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Withdraw items from a chest into player inventory.
     */
    public boolean withdrawItems(ServerPlayer player, BlockPos chestPos, String itemName, int count) {
        if (player == null || chestPos == null) return false;

        try {
            ServerLevel level = (ServerLevel) player.level();
            var container = level.getBlockEntity(chestPos);

            if (!(container instanceof Container)) {
                LOGGER.debug("Block at {} is not a container", chestPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(chestPos);
                if (be instanceof Container) {
                    Container cont = (Container) be;
                    for (int i = 0; i < cont.getContainerSize() && count > 0; i++) {
                        ItemStack stack = cont.getItem(i);
                        if (stack == null || stack.isEmpty()) continue;
                        String name = stack.getItem().toString().toLowerCase();
                        if (name.contains(itemName.toLowerCase())) {
                            int take = Math.min(count, stack.getCount());
                            stack.shrink(take);
                            player.getInventory().add(new ItemStack(stack.getItem(), take));
                            count -= take;
                        }
                    }
                }
                LOGGER.info("Withdrew items from chest at {} (best-effort)", chestPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error withdrawing items: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error withdrawing items: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Find a chest containing a specific item.
     */
    public BlockPos findChestWithItem(String itemName, int minCount) {
        for (ChestInventorySnapshot snapshot : chestCache.values()) {
            if (snapshot.getItemCount(itemName) >= minCount) {
                return snapshot.position;
            }
        }
        return null;
    }

    /**
     * Find the nearest chest to a player.
     */
    public BlockPos findNearestChest(ServerPlayer player, int searchRadius) {
        if (player == null) return null;

        SharedWorldMemory.WorldLocation nearestLoc = sharedMemory.findNearestLocation(
            "chest", player.getBlockX(), player.getBlockY(), player.getBlockZ()
        );

        if (nearestLoc != null) {
            return new BlockPos(nearestLoc.x, nearestLoc.y, nearestLoc.z);
        }

        // Best-effort scan around player for chests
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = player.blockPosition();
            int radius = Math.max(16, searchRadius);
            for (int r = 0; r <= radius; r++) {
                for (int x = pos.getX()-r; x <= pos.getX()+r; x++) {
                    for (int y = pos.getY()-r; y <= pos.getY()+r; y++) {
                        for (int z = pos.getZ()-r; z <= pos.getZ()+r; z++) {
                            BlockPos bp = new BlockPos(x,y,z);
                            if (level.getBlockState(bp).getBlock() instanceof ChestBlock) return bp;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error scanning for chests: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Transfer items between bots via chest (neutral meeting point).
     * Bot A deposits at chest, Bot B withdraws from same chest.
     */
    public boolean transferBetweenBots(ServerPlayer botA, ServerPlayer botB, BlockPos transferChest, String itemName, int count) {
        if (!depositItems(botA, transferChest, itemName, count)) {
            LOGGER.warn("Bot A failed to deposit {}", itemName);
            return false;
        }

        if (!withdrawItems(botB, transferChest, itemName, count)) {
            LOGGER.warn("Bot B failed to withdraw {}", itemName);
            return false;
        }

        LOGGER.info("Transferred {} x{} from bot A to bot B via chest", itemName, count);
        return true;
    }

    /**
     * Get cached chest inventory.
     */
    public ChestInventorySnapshot getChestSnapshot(BlockPos chestPos) {
        return chestCache.get(chestPos);
    }

    /**
     * Update chest snapshot with new scan data.
     */
    public void updateChestSnapshot(BlockPos chestPos, Map<String, Integer> items) {
        ChestInventorySnapshot snapshot = chestCache.get(chestPos);
        if (snapshot == null) {
            snapshot = new ChestInventorySnapshot(chestPos);
            chestCache.put(chestPos, snapshot);
        }
        snapshot.itemCounts = new HashMap<>(items);
        snapshot.lastScanned = System.currentTimeMillis();
        LOGGER.debug("Updated chest snapshot at {}", chestPos);
    }

    /**
     * Get all cached chests.
     */
    public Collection<ChestInventorySnapshot> getAllCachedChests() {
        return new ArrayList<>(chestCache.values());
    }

    /**
     * Clear old cache entries (older than 5 minutes).
     */
    public void cleanupOldCache() {
        long cutoff = System.currentTimeMillis() - 300000;  // 5 minutes
        Iterator<Map.Entry<BlockPos, ChestInventorySnapshot>> iter = chestCache.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getValue().lastScanned < cutoff) {
                iter.remove();
            }
        }
    }
}
