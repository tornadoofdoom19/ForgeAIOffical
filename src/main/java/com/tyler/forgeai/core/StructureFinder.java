package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import com.tyler.forgeai.ai.SharedWorldMemory;

/**
 * StructureFinder: Scans for player bases, villages, strongholds, and other structures.
 * Remembers locations and reports findings to owner.
 */
public class StructureFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-structure-finder");

    private final SharedWorldMemory worldMemory;
    private final CommunicationManager comms;
    private final String botName;

    // Base detection patterns (blocks that indicate player activity)
    private static final Set<String> BASE_BLOCKS = Set.of(
        "minecraft:crafting_table", "minecraft:furnace", "minecraft:chest",
        "minecraft:bed", "minecraft:door", "minecraft:torch", "minecraft:wall_torch",
        "minecraft:lantern", "minecraft:soul_lantern", "minecraft:campfire"
    );

    // Structure detection patterns
    private static final Map<String, Set<String>> STRUCTURE_BLOCKS = Map.of(
        "village", Set.of("minecraft:composter", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:cartography_table"),
        "stronghold", Set.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks"),
        "mineshaft", Set.of("minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail"),
        "desert_temple", Set.of("minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:cut_sandstone"),
        "jungle_temple", Set.of("minecraft:mossy_cobblestone", "minecraft:vine", "minecraft:cobweb"),
        "ocean_monument", Set.of("minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine")
    );

    public StructureFinder(SharedWorldMemory worldMemory, CommunicationManager comms, String botName) {
        this.worldMemory = worldMemory;
        this.comms = comms;
        this.botName = botName;
        LOGGER.info("StructureFinder initialized for bot: {}", botName);
    }

    /**
     * Scan for bases in a radius around the player.
     */
    public List<SharedWorldMemory.WorldLocation> scanForBases(ServerLevel level, BlockPos center, int radius) {
        List<SharedWorldMemory.WorldLocation> foundBases = new ArrayList<>();
        Set<BlockPos> scanned = new HashSet<>();

        // Scan in expanding circles
        for (int r = 1; r <= radius; r += 5) {
            for (int x = center.getX() - r; x <= center.getX() + r; x += 5) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z += 5) {
                    BlockPos pos = new BlockPos(x, center.getY(), z);
                    if (scanned.contains(pos)) continue;
                    scanned.add(pos);

                    // Check for base indicators
                    if (isBaseLocation(level, pos)) {
                        String baseName = "base_" + foundBases.size();
                        String dimension = getDimensionName(level);
                        worldMemory.registerLocation(baseName, pos.getX(), pos.getY(), pos.getZ(), dimension, "base", botName);
                        SharedWorldMemory.WorldLocation loc = worldMemory.new WorldLocation(
                            baseName, pos.getX(), pos.getY(), pos.getZ(), dimension, "base", botName
                        );
                        foundBases.add(loc);
                        LOGGER.info("Found base at: {}, {}, {} in {}", pos.getX(), pos.getY(), pos.getZ(), dimension);
                    }
                }
            }
        }

        return foundBases;
    }

    /**
     * Scan for structures in a radius around the player.
     */
    public List<SharedWorldMemory.WorldLocation> scanForStructures(ServerLevel level, BlockPos center, int radius) {
        List<SharedWorldMemory.WorldLocation> foundStructures = new ArrayList<>();
        Set<BlockPos> scanned = new HashSet<>();

        // Scan in expanding circles
        for (int r = 1; r <= radius; r += 10) {
            for (int x = center.getX() - r; x <= center.getX() + r; x += 10) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z += 10) {
                    BlockPos pos = new BlockPos(x, center.getY(), z);
                    if (scanned.contains(pos)) continue;
                    scanned.add(pos);

                    // Check for structure indicators
                    String structureType = detectStructure(level, pos);
                    if (structureType != null) {
                        String structName = structureType + "_" + foundStructures.size();
                        String dimension = getDimensionName(level);
                        worldMemory.registerLocation(structName, pos.getX(), pos.getY(), pos.getZ(), dimension, structureType, botName);
                        SharedWorldMemory.WorldLocation loc = worldMemory.new WorldLocation(
                            structName, pos.getX(), pos.getY(), pos.getZ(), dimension, structureType, botName
                        );
                        foundStructures.add(loc);
                        LOGGER.info("Found {} at: {}, {}, {} in {}", structureType, pos.getX(), pos.getY(), pos.getZ(), dimension);
                    }
                }
            }
        }

        return foundStructures;
    }

    /**
     * Check if a position indicates a player base.
     */
    private boolean isBaseLocation(ServerLevel level, BlockPos pos) {
        // Check a 5x5x5 area around the position
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    String blockName = state.getBlock().getDescriptionId();
                    if (BASE_BLOCKS.contains(blockName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Detect what type of structure is at a position.
     */
    private String detectStructure(ServerLevel level, BlockPos pos) {
        Map<String, Integer> blockCounts = new HashMap<>();

        // Count blocks in a larger area
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    String blockName = state.getBlock().getDescriptionId();
                    blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);
                }
            }
        }

        // Check for structure signatures
        for (Map.Entry<String, Set<String>> entry : STRUCTURE_BLOCKS.entrySet()) {
            String structureType = entry.getKey();
            Set<String> signatureBlocks = entry.getValue();
            int matchCount = 0;
            for (String block : signatureBlocks) {
                if (blockCounts.getOrDefault(block, 0) > 2) { // At least 3 blocks of this type
                    matchCount++;
                }
            }
            if (matchCount >= 2) { // At least 2 signature blocks present
                return structureType;
            }
        }

        return null;
    }

    /**
     * Get dimension name from level.
     */
    private String getDimensionName(ServerLevel level) {
        String dimName = level.dimension().toString().toLowerCase();
        if (dimName.contains("nether")) return "nether";
        if (dimName.contains("end") || dimName.contains("the_end")) return "end";
        return "overworld";
    }

    /**
     * Report findings to the owner.
     */
    public void reportFindings(List<SharedWorldMemory.WorldLocation> locations, String findingType) {
        if (locations.isEmpty()) {
            comms.sendMessageToOwner(botName + ": No " + findingType + " found in scan area.");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(botName).append(": Found ").append(locations.size()).append(" ").append(findingType).append("(s):\n");
        for (SharedWorldMemory.WorldLocation loc : locations) {
            message.append("- ").append(loc.type).append(" at ").append(loc.x).append(", ").append(loc.y).append(", ").append(loc.z).append("\n");
        }
        comms.sendMessageToOwner(message.toString().trim());
    }
}
