package com.tyler.forgeai.modules.gatherer;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class GathererModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-gatherer");

    private boolean active = false;
    private boolean collectAllMode = false;

    // All mineable blocks in Minecraft
    private static final Set<String> MINEABLE_BLOCKS = Set.of(
        // Ores
        "coal_ore", "iron_ore", "gold_ore", "diamond_ore", "emerald_ore", "lapis_ore", "redstone_ore",
        "copper_ore", "quartz_ore", "nether_gold_ore", "ancient_debris",
        // Stones
        "stone", "cobblestone", "andesite", "diorite", "granite", "deepslate", "cobbled_deepslate",
        "tuff", "calcite", "smooth_basalt", "blackstone", "basalt", "polished_blackstone",
        // Woods
        "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
        "crimson_stem", "warped_stem", "mangrove_log", "cherry_log", "bamboo_block",
        // Other resources
        "dirt", "grass_block", "sand", "red_sand", "gravel", "clay", "snow_block", "ice", "packed_ice",
        "blue_ice", "soul_sand", "soul_soil", "mycelium", "podzol", "coarse_dirt", "rooted_dirt",
        "moss_block", "mud", "muddy_mangrove_roots", "sponge", "wet_sponge", "seagrass", "kelp",
        "glowstone", "sea_lantern", "prismarine", "prismarine_bricks", "dark_prismarine",
        "magma_block", "obsidian", "crying_obsidian", "netherrack", "nether_bricks",
        "red_nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks",
        "end_stone", "end_stone_bricks", "purpur_block", "purpur_pillar"
    );

    // Collectible items (flowers, crops, etc. that can be picked up)
    private static final Set<String> COLLECTIBLE_ITEMS = Set.of(
        // Flowers
        "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
        "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose",
        "sunflower", "lilac", "rose_bush", "peony", "pitcher_plant", "torchflower",
        // Crops
        "wheat", "carrots", "potatoes", "beetroots", "melon", "pumpkin", "cactus", "sugar_cane",
        "bamboo", "sweet_berry_bush", "glow_berries", "chorus_fruit", "cocoa",
        // Other collectibles
        "brown_mushroom", "red_mushroom", "nether_wart", "warped_fungus", "crimson_fungus",
        "weeping_vines", "twisting_vines", "vine", "lily_pad", "sea_pickle", "kelp", "seagrass"
    );

    public void init() {
        LOGGER.info("Gatherer module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Gatherer module active: " + enabled);
    }

    public void setCollectAllMode(boolean collectAll) {
        collectAllMode = collectAll;
        LOGGER.info("Collect all mode: " + collectAll);
    }

    /**
     * Check if an item is collectible (flowers, crops, etc.)
     */
    public static boolean isCollectible(String itemName) {
        if (itemName == null) return false;
        return COLLECTIBLE_ITEMS.contains(itemName.toLowerCase());
    }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Gatherer tick running for player: " + s.player.getName().getString());

        if (collectAllMode || s.needsResources()) {
            collectResources(s);
        } else {
            LOGGER.debug("No resource need detected — GathererModule idle.");
        }
    }

    private void collectResources(Signals s) {
        try {
            var player = s.player;
            if (player == null) return;

            boolean gatheredAny = false;

            if (collectAllMode) {
                // Collect all mineable blocks
                gatheredAny = collectAllMineableBlocks(player);
            } else {
                // Collect basic resources
                String[] resources = new String[]{"log", "iron_ore", "coal_ore", "stone", "dirt", "oak_log", "copper_ore"};
                gatheredAny = collectSpecificResources(player, resources);
            }

            if (!gatheredAny) {
                LOGGER.debug("No immediate resources found to gather");
            } else {
                LOGGER.info("Gathered resources successfully");
            }
        } catch (Exception e) {
            LOGGER.debug("collectResources error: {}", e.getMessage());
        }
    }

    private boolean collectAllMineableBlocks(net.minecraft.server.level.ServerPlayer player) {
        boolean gatheredAny = false;
        int searchRadius = 16;

        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
            player.blockPosition().offset(-searchRadius, -4, -searchRadius),
            player.blockPosition().offset(searchRadius, 8, searchRadius))) {

            var state = player.level().getBlockState(pos);
            String blockName = state.getBlock().getDescriptionId().replace("block.minecraft.", "");

            if (MINEABLE_BLOCKS.contains(blockName)) {
                try {
                    com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, pos);
                    com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, pos);
                    gatheredAny = true;
                    try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    break; // Break one block per tick to avoid lag
                } catch (Exception e) {
                    LOGGER.debug("Failed to break block {}: {}", blockName, e.getMessage());
                }
            }
        }

        return gatheredAny;
    }

    public boolean collectSpecificItem(String itemName) {
        // This method can be called externally to collect a specific item
        var player = getPlayerFromContext();
        if (player != null) {
            return collectSpecificItem(player, itemName);
        }
        return false;
    }

    private net.minecraft.server.level.ServerPlayer getPlayerFromContext() {
        // This would need to be passed in or accessed from a context
        // For now, return null - this should be improved
        return null;
    }
}
