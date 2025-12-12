package com.tyler.forgeai.util;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * LitematicaIntegration: Parse and utilize Litematica schematic files for building.
 * - Load .litematic schematic files
 * - Compute material requirement list
 * - Track placement progress
 * - Generate subtasks for multi-bot building
 */
public class LitematicaIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-litematica");

    public static class SchematicData {
        public String name;
        public BlockPos size;           // Width, Height, Depth
        public BlockPos offset;         // Placement offset
        public List<BlockEntry> blocks;
        public Map<String, Integer> materialRequirements;

        public SchematicData(String name) {
            this.name = name;
            this.blocks = new ArrayList<>();
            this.materialRequirements = new HashMap<>();
        }

        @Override
        public String toString() {
            return String.format("Schematic{%s, size=%dx%dx%d, blocks=%d}", 
                name, size.getX(), size.getY(), size.getZ(), blocks.size());
        }
    }

    public static class BlockEntry {
        public BlockPos pos;
        public String blockName;
        public String properties;  // e.g., "facing=north,half=upper"

        public BlockEntry(BlockPos pos, String blockName, String properties) {
            this.pos = pos;
            this.blockName = blockName;
            this.properties = properties;
        }
    }

    public static class MaterialRequirement {
        public String itemName;
        public int count;
        public String source;  // where to find it (mine, craft, trade, chest)

        public MaterialRequirement(String itemName, int count) {
            this.itemName = itemName;
            this.count = count;
            this.source = "unknown";
        }

        @Override
        public String toString() {
            return String.format("%s x%d (%s)", itemName, count, source);
        }
    }

    /**
     * Load schematic from .litematic file (NBT compressed format).
     * .litematic files are GZIP-compressed NBT with structure metadata and block data.
     */
    public static SchematicData loadSchematic(File schematicFile) {
        if (!schematicFile.exists()) {
            LOGGER.warn("Schematic file not found: {}", schematicFile.getAbsolutePath());
            return null;
        }

        try {
            // Read NBT compound from GZIP-compressed file
            FileInputStream fis = new FileInputStream(schematicFile);
            GZIPInputStream gzip = new GZIPInputStream(fis);
            CompoundTag rootTag = NbtIo.read(gzip);
            gzip.close();
            fis.close();

            if (rootTag == null) {
                LOGGER.warn("Failed to read NBT from schematic file");
                return null;
            }

            SchematicData schematic = new SchematicData(schematicFile.getName());

            // Read metadata
            if (rootTag.contains("Metadata", Tag.TAG_COMPOUND)) {
                CompoundTag metadata = rootTag.getCompound("Metadata");
                schematic.name = metadata.getString("Name");
                schematic.offset = BlockPos.ZERO;  // Offset from origin
            }

            // Read regions (Litematica can have multiple regions)
            if (rootTag.contains("Regions", Tag.TAG_LIST)) {
                ListTag regionsList = rootTag.getList("Regions", Tag.TAG_COMPOUND);

                for (int i = 0; i < regionsList.size(); i++) {
                    CompoundTag regionTag = regionsList.getCompound(i);
                    
                    // Get region bounds
                    CompoundTag pos = regionTag.getCompound("Position");
                    int x = pos.getInt("x");
                    int y = pos.getInt("y");
                    int z = pos.getInt("z");
                    
                    CompoundTag size = regionTag.getCompound("Size");
                    int width = size.getInt("x");
                    int height = size.getInt("y");
                    int depth = size.getInt("z");
                    
                    schematic.size = new BlockPos(width, height, depth);

                    // Read block palette (list of block names)
                    ListTag paletteList = regionTag.getList("Palette", Tag.TAG_COMPOUND);
                    Map<Integer, String> blockPalette = new HashMap<>();
                    for (int j = 0; j < paletteList.size(); j++) {
                        CompoundTag blockTag = paletteList.getCompound(j);
                        String blockName = blockTag.getString("Name");
                        blockPalette.put(j, blockName);
                    }

                    // Read block state data (encoded block IDs at each position)
                    if (regionTag.contains("BlockStates", Tag.TAG_BYTE_ARRAY)) {
                        byte[] blockStates = regionTag.getByteArray("BlockStates");
                        
                        // Decode block state array
                        // Format: variable-length encoding of block palette indices
                        List<Integer> blockIndices = decodeBlockStates(blockStates, paletteList.size());
                        
                        // Map indices to positions and create block entries
                        int blockIdx = 0;
                        for (int bx = 0; bx < width && blockIdx < blockIndices.size(); bx++) {
                            for (int by = 0; by < height && blockIdx < blockIndices.size(); by++) {
                                for (int bz = 0; bz < depth && blockIdx < blockIndices.size(); bz++) {
                                    int paletteIndex = blockIndices.get(blockIdx);
                                    String blockName = blockPalette.getOrDefault(paletteIndex, "minecraft:air");
                                    
                                    // Skip air blocks
                                    if (!blockName.contains("air")) {
                                        BlockPos blockPos = new BlockPos(x + bx, y + by, z + bz);
                                        
                                        // Extract block name and properties
                                        String[] parts = blockName.split("\\[");
                                        String cleanBlockName = parts[0];
                                        String properties = parts.length > 1 ? "[" + parts[1] : "";
                                        
                                        schematic.blocks.add(new BlockEntry(blockPos, cleanBlockName, properties));
                                    }
                                    blockIdx++;
                                }
                            }
                        }
                    }
                }
            }

            LOGGER.info("Loaded schematic: {} ({} blocks, size: {}x{}x{})", 
                schematic.name, schematic.blocks.size(), 
                schematic.size.getX(), schematic.size.getY(), schematic.size.getZ());
            
            return schematic;
        } catch (Exception e) {
            LOGGER.error("Error loading schematic: {}", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decode variable-length encoded block state array.
     * Litematica uses bit-packed encoding for block palette indices.
     */
    private static List<Integer> decodeBlockStates(byte[] data, int paletteSize) {
        List<Integer> indices = new ArrayList<>();
        
        // Calculate bits needed for palette size
        int bitsPerEntry = Math.max(1, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        
        // Convert bytes to long array for bit manipulation
        long[] longArray = new long[data.length / 8];
        for (int i = 0; i < longArray.length; i++) {
            longArray[i] = ((long) data[i * 8] & 0xFFL) |
                          (((long) data[i * 8 + 1] & 0xFFL) << 8) |
                          (((long) data[i * 8 + 2] & 0xFFL) << 16) |
                          (((long) data[i * 8 + 3] & 0xFFL) << 24) |
                          (((long) data[i * 8 + 4] & 0xFFL) << 32) |
                          (((long) data[i * 8 + 5] & 0xFFL) << 40) |
                          (((long) data[i * 8 + 6] & 0xFFL) << 48) |
                          (((long) data[i * 8 + 7] & 0xFFL) << 56);
        }
        
        // Decode values
        long mask = (1L << bitsPerEntry) - 1;
        int entryIndex = 0;
        int bitPosition = 0;
        
        while (entryIndex < (16 * 16 * 16) && bitPosition / 64 < longArray.length) {
            int longIndex = bitPosition / 64;
            int localBit = bitPosition % 64;
            
            long value;
            if (localBit + bitsPerEntry <= 64) {
                // Value fits within single long
                value = (longArray[longIndex] >> localBit) & mask;
            } else {
                // Value spans two longs
                int bitsFromFirst = 64 - localBit;
                long part1 = (longArray[longIndex] >> localBit) & ((1L << bitsFromFirst) - 1);
                long part2 = longArray[longIndex + 1] & ((1L << (bitsPerEntry - bitsFromFirst)) - 1);
                value = part1 | (part2 << bitsFromFirst);
            }
            
            indices.add((int) (value & mask));
            bitPosition += bitsPerEntry;
            entryIndex++;
        }
        
        return indices;
    }

    /**
     * Compute material requirements from schematic.
     */
    public static Map<String, Integer> computeMaterialRequirements(SchematicData schematic) {
        Map<String, Integer> requirements = new HashMap<>();

        for (BlockEntry entry : schematic.blocks) {
            String blockName = entry.blockName.toLowerCase();
            
            // Map block names to materials (block -> item)
            String materialName = blockNameToMaterial(blockName);
            requirements.put(materialName, requirements.getOrDefault(materialName, 0) + 1);
        }

        schematic.materialRequirements = requirements;
        LOGGER.info("Computed materials for {}: {} unique items", schematic.name, requirements.size());

        return requirements;
    }

    /**
     * Convert block name to required material/item.
     */
    private static String blockNameToMaterial(String blockName) {
        // Simple mapping: block_type -> item_type
        if (blockName.contains("oak") || blockName.contains("wood")) return "oak_log";
        if (blockName.contains("stone")) return "stone";
        if (blockName.contains("dirt")) return "dirt";
        if (blockName.contains("glass")) return "glass";
        if (blockName.contains("brick")) return "bricks";
        if (blockName.contains("torch")) return "torch";
        if (blockName.contains("chest")) return "chest";
        return blockName;  // Default: use block name as item name
    }

    /**
     * Validate schematic fit at a location.
     */
    public static boolean validatePlacement(ServerLevel level, SchematicData schematic, BlockPos origin) {
        if (level == null || schematic == null || origin == null) return false;

        try {
            BlockPos end = origin.offset(schematic.size.getX(), schematic.size.getY(), schematic.size.getZ());

            // Check if all positions are loaded
            for (int x = origin.getX(); x <= end.getX(); x++) {
                for (int y = origin.getY(); y <= end.getY(); y++) {
                    for (int z = origin.getZ(); z <= end.getZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.isLoaded(pos)) {
                            LOGGER.debug("Schematic placement blocked: chunk not loaded at {}", pos);
                            return false;
                        }
                    }
                }
            }

            LOGGER.info("Schematic placement validated at {}", origin);
            return true;
        } catch (Exception e) {
            LOGGER.debug("Error validating placement: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generate placement subtasks (break into regions for parallel building).
     */
    public static List<BuildRegion> generateBuildRegions(SchematicData schematic, BlockPos origin, int regionSize) {
        List<BuildRegion> regions = new ArrayList<>();

        int regionsX = (schematic.size.getX() + regionSize - 1) / regionSize;
        int regionsY = (schematic.size.getY() + regionSize - 1) / regionSize;
        int regionsZ = (schematic.size.getZ() + regionSize - 1) / regionSize;

        for (int rx = 0; rx < regionsX; rx++) {
            for (int ry = 0; ry < regionsY; ry++) {
                for (int rz = 0; rz < regionsZ; rz++) {
                    BlockPos regionMin = origin.offset(rx * regionSize, ry * regionSize, rz * regionSize);
                    BlockPos regionMax = regionMin.offset(regionSize - 1, regionSize - 1, regionSize - 1);

                    BuildRegion region = new BuildRegion(rx, ry, rz, regionMin, regionMax);

                    // Count blocks in this region
                    for (BlockEntry block : schematic.blocks) {
                        if (block.pos.getX() >= regionMin.getX() && block.pos.getX() <= regionMax.getX() &&
                            block.pos.getY() >= regionMin.getY() && block.pos.getY() <= regionMax.getY() &&
                            block.pos.getZ() >= regionMin.getZ() && block.pos.getZ() <= regionMax.getZ()) {
                            region.blockCount++;
                        }
                    }

                    if (region.blockCount > 0) {
                        regions.add(region);
                    }
                }
            }
        }

        LOGGER.info("Generated {} build regions for schematic {}", regions.size(), schematic.name);
        return regions;
    }

    public static class BuildRegion {
        public int rx, ry, rz;  // Region grid position
        public BlockPos min, max;
        public int blockCount;
        public boolean completed;

        public BuildRegion(int rx, int ry, int rz, BlockPos min, BlockPos max) {
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
            this.min = min;
            this.max = max;
            this.blockCount = 0;
            this.completed = false;
        }

        @Override
        public String toString() {
            return String.format("Region[%d,%d,%d]: %d blocks", rx, ry, rz, blockCount);
        }
    }

    /**
     * Get placement progress (percentage of blocks placed).
     */
    public static double getPlacementProgress(ServerLevel level, SchematicData schematic, BlockPos origin) {
        if (level == null || schematic == null || origin == null) return 0.0;

        int placed = 0;
        int total = schematic.blocks.size();

        try {
            for (BlockEntry entry : schematic.blocks) {
                BlockPos worldPos = origin.offset(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
                var blockState = level.getBlockState(worldPos);

                if (blockState.getBlock().getName().toString().contains(entry.blockName.toLowerCase())) {
                    placed++;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error computing progress: {}", e.getMessage());
        }

        return total > 0 ? (double) placed / total * 100 : 0;
    }

    /**
     * Simulate a build on the level without placing blocks to detect dangerous placements.
     */
    public static com.tyler.forgeai.core.BuildSafetyManager.ValidationResult simulateBuildCheck(SchematicData schematic, ServerLevel level, BlockPos origin, boolean allowOverwrite) {
        return com.tyler.forgeai.core.BuildSafetyManager.simulateBuild(schematic, level, origin, allowOverwrite);
    }

    /**
     * Get next unplaced block for building.
     */
    public static BlockEntry getNextBlockToBuild(ServerLevel level, SchematicData schematic, BlockPos origin) {
        try {
            for (BlockEntry entry : schematic.blocks) {
                BlockPos worldPos = origin.offset(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
                var blockState = level.getBlockState(worldPos);

                if (!blockState.getBlock().getName().toString().contains(entry.blockName.toLowerCase())) {
                    return entry;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error finding next block: {}", e.getMessage());
        }

        return null;  // All blocks placed
    }
}
