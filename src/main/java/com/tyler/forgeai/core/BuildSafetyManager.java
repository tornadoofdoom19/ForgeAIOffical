package com.tyler.forgeai.core;

import com.tyler.forgeai.util.LitematicaIntegration;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * BuildSafetyManager: validates schematics and enforces safe builds.
 */
public class BuildSafetyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-build-safety");

    // Blocks considered dangerous/triggering in a build context.
    private static final Set<String> DANGEROUS_PREFIXES = Set.of(
        "minecraft:tnt", "minecraft:dispenser", "minecraft:dropper", "minecraft:observer",
        "minecraft:repeater", "minecraft:comparator", "minecraft:redstone_block", "minecraft:lava"
    );

    public static class ValidationResult {
        public boolean ok = true;
        public final List<String> reasons = new ArrayList<>();
        public boolean dangerousBlocksFound = false;
    }

    /**
     * Simulate the build without making changes. Returns a ValidationResult with issues.
     */
    public static ValidationResult simulateBuild(LitematicaIntegration.SchematicData schematic, ServerLevel level, BlockPos origin, boolean allowOverwrite) {
        ValidationResult res = new ValidationResult();
        if (schematic == null) {
            res.ok = false; res.reasons.add("no schematic provided"); return res;
        }
        if (level == null) {
            res.ok = false; res.reasons.add("server level null"); return res;
        }
        // Check fit and collisions
        for (LitematicaIntegration.BlockEntry e : schematic.blocks) {
            BlockPos worldPos = origin.offset(e.pos.getX(), e.pos.getY(), e.pos.getZ());
            // If existing non-air block and not allowed to overwrite, fail
            var state = level.getBlockState(worldPos);
            var block = state.getBlock();
            boolean isAir = block.getName().getString().toLowerCase().contains("air");
            if (!isAir && !allowOverwrite) {
                res.ok = false;
                res.reasons.add("Existing block at " + worldPos + " would be overwritten: " + block.getName());
            }
            // Check for dangerous block types in schematic itself
            String bn = e.blockName.toLowerCase();
            for (String pref : DANGEROUS_PREFIXES) {
                if (bn.contains(pref.substring(pref.indexOf(":") + 1))) {
                    res.dangerousBlocksFound = true;
                    res.reasons.add("Dangerous block in schematic: " + e.blockName + " at " + e.pos);
                }
            }
        }
        return res;
    }

    public static boolean isDangerousBlock(String blockName) {
        if (blockName == null) return false;
        String lower = blockName.toLowerCase();
        for (String pref : DANGEROUS_PREFIXES) if (lower.contains(pref.substring(pref.indexOf(":") + 1))) return true;
        return false;
    }
}
