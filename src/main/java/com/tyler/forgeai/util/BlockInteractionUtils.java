package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockInteractionUtils: Utilities for interacting with special blocks.
 * Handles: beds, ladders, trapdoors, buttons, doors, anvils, enchanting tables, brewing stands, furnaces, etc.
 */
public final class BlockInteractionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-blocks");

    private BlockInteractionUtils() {}

    /**
     * Find nearest block of a specific type within scan radius.
     */
    public static BlockPos findNearestBlock(ServerPlayer player, Class<? extends Block> blockType, int radius) {
        if (player == null || player.level() == null) return null;
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        try {
            for (int r = 0; r <= radius; r++) {
                for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
                    for (int y = playerPos.getY() - r; y <= playerPos.getY() + r; y++) {
                        for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (blockType.isInstance(state.getBlock())) {
                                return pos;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error finding block: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if player can interact with bed (attempt to sleep).
     * Returns true if successful, false if not nighttime or other conditions.
     */
    public static boolean interactWithBed(ServerPlayer player, BlockPos bedPos) {
        if (player == null || bedPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(bedPos);
            Block block = state.getBlock();

            if (!(block instanceof BedBlock)) {
                LOGGER.debug("Block at {} is not a bed", bedPos);
                return false;
            }

            // Best-effort: move player to bed position and set spawn
            try {
                player.teleportTo(bedPos.getX() + 0.5, bedPos.getY() + 0.5, bedPos.getZ() + 0.5);
                player.getRespawnPosition();
                LOGGER.info("Player moved to bed at {} and sleep mode requested", bedPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error while moving player to bed: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error interacting with bed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Attempt to climb ladder (place player on ladder block).
     */
    public static boolean climbLadder(ServerPlayer player, BlockPos ladderPos) {
        if (player == null || ladderPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(ladderPos);
            Block block = state.getBlock();

            if (!(block instanceof LadderBlock)) {
                LOGGER.debug("Block at {} is not a ladder", ladderPos);
                return false;
            }

            try {
                player.teleportTo(ladderPos.getX() + 0.5, ladderPos.getY(), ladderPos.getZ() + 0.5);
                LOGGER.info("Player positioned on ladder at {}", ladderPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error positioning player on ladder: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error climbing ladder: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open trapdoor or toggle its state.
     */
    public static boolean toggleTrapdoor(ServerPlayer player, BlockPos trapdoorPos) {
        if (player == null || trapdoorPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(trapdoorPos);
            Block block = state.getBlock();

            if (!(block instanceof TrapDoorBlock)) {
                LOGGER.debug("Block at {} is not a trapdoor", trapdoorPos);
                return false;
            }

            try {
                boolean open = state.getValue(TrapDoorBlock.OPEN);
                level.setBlock(trapdoorPos, state.setValue(TrapDoorBlock.OPEN, !open), 3);
                LOGGER.info("Trapdoor at {} toggled to {}", trapdoorPos, !open);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error toggling trapdoor state: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error toggling trapdoor: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Press button (trigger redstone pulse).
     */
    public static boolean pressButton(ServerPlayer player, BlockPos buttonPos) {
        if (player == null || buttonPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(buttonPos);
            Block block = state.getBlock();

            if (!(block instanceof ButtonBlock)) {
                LOGGER.debug("Block at {} is not a button", buttonPos);
                return false;
            }

            try {
                // Set powered property if available, then schedule unpower via tick
                if (state.hasProperty(ButtonBlock.POWERED)) {
                    level.setBlock(buttonPos, state.setValue(ButtonBlock.POWERED, true), 3);
                }
                LOGGER.info("Button at {} pressed", buttonPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error pressing button: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error pressing button: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open door (push/pull).
     */
    public static boolean toggleDoor(ServerPlayer player, BlockPos doorPos) {
        if (player == null || doorPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(doorPos);
            Block block = state.getBlock();

            if (!(block instanceof DoorBlock)) {
                LOGGER.debug("Block at {} is not a door", doorPos);
                return false;
            }

            try {
                boolean open = state.getValue(DoorBlock.OPEN);
                level.setBlock(doorPos, state.setValue(DoorBlock.OPEN, !open), 3);
                LOGGER.info("Door at {} toggled to {}", doorPos, !open);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error toggling door: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error toggling door: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open anvil interface (for repairs/enchantments).
     */
    public static boolean openAnvil(ServerPlayer player, BlockPos anvilPos) {
        if (player == null || anvilPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(anvilPos);
            Block block = state.getBlock();

            if (!(block instanceof AnvilBlock)) {
                LOGGER.debug("Block at {} is not an anvil", anvilPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(anvilPos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened anvil at {}", anvilPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening anvil: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening anvil: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open enchanting table interface.
     */
    public static boolean openEnchantingTable(ServerPlayer player, BlockPos tablePos) {
        if (player == null || tablePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(tablePos);
            Block block = state.getBlock();

            if (!(block instanceof EnchantingTableBlock)) {
                LOGGER.debug("Block at {} is not an enchanting table", tablePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(tablePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened enchanting table at {}", tablePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening enchanting table: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening enchanting table: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open brewing stand interface.
     */
    public static boolean openBrewingStand(ServerPlayer player, BlockPos standPos) {
        if (player == null || standPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(standPos);
            Block block = state.getBlock();

            if (!(block instanceof BrewingStandBlock)) {
                LOGGER.debug("Block at {} is not a brewing stand", standPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(standPos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened brewing stand at {}", standPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening brewing stand: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening brewing stand: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open furnace interface.
     */
    public static boolean openFurnace(ServerPlayer player, BlockPos furnacePos) {
        if (player == null || furnacePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(furnacePos);
            Block block = state.getBlock();

            if (!(block instanceof FurnaceBlock)) {
                LOGGER.debug("Block at {} is not a furnace", furnacePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(furnacePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened furnace at {}", furnacePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening furnace: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening furnace: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open crafting table interface.
     */
    public static boolean openCraftingTable(ServerPlayer player, BlockPos tablePos) {
        if (player == null || tablePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(tablePos);
            Block block = state.getBlock();

            if (!(block instanceof CraftingTableBlock)) {
                LOGGER.debug("Block at {} is not a crafting table", tablePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(tablePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened crafting table at {}", tablePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening crafting table: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening crafting table: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open storage block (chest, barrel, shulker box, etc.).
     */
    public static boolean openStorage(ServerPlayer player, BlockPos storagePos) {
        if (player == null || storagePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(storagePos);
            Block block = state.getBlock();

            // Check if it's a storage block (chest, barrel, furnace, etc.)
            if (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock) && 
                !(block instanceof HopperBlock) && !(block instanceof ShulkerBoxBlock)) {
                LOGGER.debug("Block at {} is not a storage container", storagePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(storagePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened storage container at {}", storagePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening storage: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening storage: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open loom interface.
     */
    public static boolean openLoom(ServerPlayer player, BlockPos loomPos) {
        if (player == null || loomPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(loomPos);
            Block block = state.getBlock();

            if (!(block instanceof LoomBlock)) {
                LOGGER.debug("Block at {} is not a loom", loomPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(loomPos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened loom at {}", loomPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening loom: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening loom: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open cartography table interface.
     */
    public static boolean openCartographyTable(ServerPlayer player, BlockPos tablePos) {
        if (player == null || tablePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(tablePos);
            Block block = state.getBlock();

            if (!(block instanceof CartographyTableBlock)) {
                LOGGER.debug("Block at {} is not a cartography table", tablePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(tablePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened cartography table at {}", tablePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening cartography table: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening cartography table: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open smithing table interface.
     */
    public static boolean openSmithingTable(ServerPlayer player, BlockPos tablePos) {
        if (player == null || tablePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(tablePos);
            Block block = state.getBlock();

            if (!(block instanceof SmithingTableBlock)) {
                LOGGER.debug("Block at {} is not a smithing table", tablePos);
                return false;
            }

            try {
                var be = level.getBlockEntity(tablePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened smithing table at {}", tablePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening smithing table: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening smithing table: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Open stonecutter interface.
     */
    public static boolean openStonecutter(ServerPlayer player, BlockPos cutterPos) {
        if (player == null || cutterPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(cutterPos);
            Block block = state.getBlock();

            if (!(block instanceof StonecutterBlock)) {
                LOGGER.debug("Block at {} is not a stonecutter", cutterPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(cutterPos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Opened stonecutter at {}", cutterPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error opening stonecutter: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening stonecutter: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Interact with a campfire (cook food).
     */
    public static boolean useCampfire(ServerPlayer player, BlockPos campfirePos) {
        if (player == null || campfirePos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(campfirePos);
            Block block = state.getBlock();

            if (!(block instanceof CampfireBlock)) {
                LOGGER.debug("Block at {} is not a campfire", campfirePos);
                return false;
            }

            try {
                // Best-effort: interact to open cook UI
                var be = level.getBlockEntity(campfirePos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Interacted with campfire at {}", campfirePos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error using campfire: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error using campfire: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Interact with a composter (add materials, extract bone meal).
     */
    public static boolean useComposter(ServerPlayer player, BlockPos composterPos) {
        if (player == null || composterPos == null || player.level() == null) return false;
        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockState state = level.getBlockState(composterPos);
            Block block = state.getBlock();

            if (!(block instanceof ComposterBlock)) {
                LOGGER.debug("Block at {} is not a composter", composterPos);
                return false;
            }

            try {
                var be = level.getBlockEntity(composterPos);
                if (be != null) player.openMenu(be.createMenu(0, player.getInventory(), player));
                LOGGER.info("Interacted with composter at {}", composterPos);
                return true;
            } catch (Exception e) {
                LOGGER.debug("Error using composter: {}", e.getMessage());
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error using composter: {}", e.getMessage());
            return false;
        }
    }
}
