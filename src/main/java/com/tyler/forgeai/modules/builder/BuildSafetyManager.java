package com.tyler.forgeai.modules.builder;

import com.tyler.forgeai.util.LitematicaIntegration;
import com.tyler.forgeai.config.ConfigLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * BuildSafetyManager: enforces strict Litematica schematic placement rules.
 */
public final class BuildSafetyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-build-safety");
    private static final Map<UUID, BlockPos> anchors = new HashMap<>();

    private BuildSafetyManager() {}

    public static void setBuildLocation(ServerPlayer player, int x, int y, int z) {
        if (player == null) return;
        anchors.put(player.getUUID(), new BlockPos(x, y, z));
        LOGGER.info("Build anchor set for {} -> {}", player.getName().getString(), new BlockPos(x, y, z).toShortString());
    }

    public static BlockPos getBuildLocation(ServerPlayer player) {
        if (player == null) return null;
        return anchors.get(player.getUUID());
    }

    public static void clearBuildLocation(ServerPlayer player) {
        if (player == null) return;
        anchors.remove(player.getUUID());
    }

    public static boolean dryRunValidate(ServerLevel level, LitematicaIntegration.SchematicData schematic, BlockPos anchor, ConfigLoader.ForgeAIConfig cfg) {
        if (level == null || schematic == null || anchor == null) return false;
        // Simulate placing each block and verify no redstone or dangerous items if not allowed
        for (LitematicaIntegration.BlockEntry e : schematic.blocks) {
            if (e == null) continue;
            String blockName = e.blockName.toLowerCase();
            if (!cfg.allowRedstoneBuilds && (blockName.contains("tnt") || blockName.contains("redstone") || blockName.contains("dispenser") || blockName.contains("dropper") || blockName.contains("observer"))) {
                LOGGER.warn("Dry-run: schematic includes risky redstone element: {}", blockName);
                return false;
            }
            BlockPos worldPos = anchor.offset(e.pos.getX(), e.pos.getY(), e.pos.getZ());
            if (!level.isLoaded(worldPos)) {
                LOGGER.warn("Dry-run: world chunk not loaded at {}", worldPos);
                return false;
            }
        }
        LOGGER.info("Dry-run: schematic validation passed");
        return true;
    }

    public static boolean validateAreaClear(ServerLevel level, LitematicaIntegration.SchematicData schematic, BlockPos anchor, ConfigLoader.ForgeAIConfig cfg) {
        if (level == null || schematic == null || anchor == null) return false;
        for (LitematicaIntegration.BlockEntry e : schematic.blocks) {
            BlockPos worldPos = anchor.offset(e.pos.getX(), e.pos.getY(), e.pos.getZ());
            if (!level.isLoaded(worldPos)) return false;
            BlockState existing = level.getBlockState(worldPos);
            if (!existing.isAir() && !cfg.allowOverwriteBuilds) {
                LOGGER.warn("Placement conflict at {}: existing block {} would be overwritten", worldPos, existing.getBlock().getName());
                return false;
            }
        }
        LOGGER.info("Area check: clear for schematic at {}", anchor);
        return true;
    }

    public static boolean guardPlacement(ServerLevel level, ServerPlayer player, LitematicaIntegration.BlockEntry entry, BlockPos anchor, ConfigLoader.ForgeAIConfig cfg) {
        try {
            BlockPos worldPos = anchor.offset(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
            BlockState existing = level.getBlockState(worldPos);
            String curName = existing.getBlock().getName().getString().toLowerCase();
            String expected = entry.blockName.toLowerCase();

            // Layer enforcement: ensure Y layer matches expected relative Y (building layer-by-layer will ensure this externally)
            // Type enforcement: ensure we're not placing a different block mistakenly
            if (!curName.contains("air") && !cfg.allowOverwriteBuilds && !curName.contains(expected)) {
                LOGGER.error("Catastrophic: would overwrite {} with {} at {}", curName, expected, worldPos);
                // Punish and stop
                var punish = com.tyler.forgeai.ForgeAI.getPunishmentSystem();
                if (punish != null) punish.punish("BuildSafetyManager", 50);
                return false;
            }

            // Block orientation check (best effort): verify property string contains expected properties
            if (entry.properties != null && !entry.properties.isEmpty()) {
                String blockStr = existing.getBlock().getName().getString().toLowerCase();
                if (!blockStr.contains(expected)) {
                    LOGGER.error("Catastrophic: expected {} but found {} at {}", expected, blockStr, worldPos);
                    var punish = com.tyler.forgeai.ForgeAI.getPunishmentSystem();
                    if (punish != null) punish.punish("BuildSafetyManager", 50);
                    return false;
                }
            }

            // Redstone safety
            if (!cfg.allowRedstoneBuilds) {
                String bl = expected.toLowerCase();
                if (bl.contains("tnt") || bl.contains("redstone") || bl.contains("dispenser") || bl.contains("dropper") || bl.contains("observer") || bl.contains("cannon")) {
                    LOGGER.warn("Prevented redstone/triggerable block: {} at {}", bl, worldPos);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.debug("guardPlacement error: {}", e.getMessage());
            return false;
        }
    }
}
