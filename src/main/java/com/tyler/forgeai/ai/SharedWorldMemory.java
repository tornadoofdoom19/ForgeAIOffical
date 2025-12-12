package com.tyler.forgeai.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * SharedWorldMemory: Persistent memory shared across all bots in the same world.
 * Stores locations (beds, chests, portals, bases), block caches, and allows bots to query/update.
 * Backed by world NBT data so it persists across sessions.
 */
public class SharedWorldMemory {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-world-memory");

    // In-memory cache
    private final Map<String, WorldLocation> locations = new HashMap<>();  // name -> location
    private final Map<String, BlockCache> blockCaches = new HashMap<>();   // type -> block cache
    private final Map<String, TrainingSnapshot> trainingSnapshots = new HashMap<>();  // bot_name -> training data

    private final ServerLevel level;
    private final String worldKey;

    public static class WorldLocation {
        public String name;
        public int x, y, z;
        public String type;  // bed, chest, portal, base, etc.
        public long lastUpdated;
        public String discoveredBy;

        public WorldLocation(String name, int x, int y, int z, String type, String discoveredBy) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.discoveredBy = discoveredBy;
            this.lastUpdated = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return String.format("Loc{%s:%s@%d,%d,%d}", name, type, x, y, z);
        }
    }

    public static class BlockCache {
        public String blockType;
        public int[] positions;  // flattened x,y,z coords
        public long lastScanned;

        public BlockCache(String blockType) {
            this.blockType = blockType;
            this.positions = new int[0];
            this.lastScanned = System.currentTimeMillis();
        }
    }

    public static class TrainingSnapshot {
        public String botName;
        public Map<String, Integer> successCounts = new HashMap<>();
        public Map<String, Integer> failureCounts = new HashMap<>();
        public long timestamp;

        public TrainingSnapshot(String botName) {
            this.botName = botName;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public SharedWorldMemory(ServerLevel level) {
        this.level = level;
        this.worldKey = level.getWorld().getLevelName();
        LOGGER.info("SharedWorldMemory initialized for world: {}", worldKey);
    }

    /**
     * Register or update a world location (bed, chest, portal, etc.)
     */
    public void registerLocation(String name, int x, int y, int z, String type, String discoveredBy) {
        WorldLocation loc = new WorldLocation(name, x, y, z, type, discoveredBy);
        locations.put(name.toLowerCase(), loc);
        LOGGER.info("Registered location: {} at {},{},{} by {}", name, x, y, z, discoveredBy);
    }

    /**
     * Find a location by name or type.
     */
    public WorldLocation getLocation(String nameOrType) {
        return locations.get(nameOrType.toLowerCase());
    }

    /**
     * Get all locations of a specific type (e.g., all beds, all chests).
     */
    public List<WorldLocation> getLocationsByType(String type) {
        List<WorldLocation> result = new ArrayList<>();
        for (WorldLocation loc : locations.values()) {
            if (loc.type.equalsIgnoreCase(type)) {
                result.add(loc);
            }
        }
        return result;
    }

    /**
     * Find nearest location of a type from player position.
     */
    public WorldLocation findNearestLocation(String type, int playerX, int playerY, int playerZ) {
        List<WorldLocation> candidates = getLocationsByType(type);
        WorldLocation nearest = null;
        double minDist = Double.MAX_VALUE;

        for (WorldLocation loc : candidates) {
            double dist = Math.sqrt(
                Math.pow(loc.x - playerX, 2) +
                Math.pow(loc.y - playerY, 2) +
                Math.pow(loc.z - playerZ, 2)
            );
            if (dist < minDist) {
                minDist = dist;
                nearest = loc;
            }
        }
        return nearest;
    }

    /**
     * Update block cache for a specific type (e.g., all diamond_ore blocks found).
     */
    public void updateBlockCache(String blockType, int... coords) {
        BlockCache cache = blockCaches.computeIfAbsent(blockType, BlockCache::new);
        cache.positions = coords;
        cache.lastScanned = System.currentTimeMillis();
        LOGGER.info("Updated block cache for {}: {} blocks found", blockType, coords.length / 3);
    }

    /**
     * Get cached block positions for a type.
     */
    public int[] getBlockCache(String blockType) {
        BlockCache cache = blockCaches.get(blockType);
        return cache != null ? cache.positions : new int[0];
    }

    /**
     * Store a bot's training snapshot so other bots can learn from it.
     */
    public void storeTrainingSnapshot(String botName, TrainingManager tm) {
        TrainingSnapshot snap = new TrainingSnapshot(botName);
        snap.successCounts = new HashMap<>(tm.getSuccessMap());
        snap.failureCounts = new HashMap<>(tm.getFailureMap());
        trainingSnapshots.put(botName, snap);
        LOGGER.info("Stored training snapshot for bot: {}", botName);
    }

    /**
     * Load another bot's training data into this bot's trainer.
     */
    public void loadTrainingFromBot(String sourceBotName, TrainingManager targetTm) {
        TrainingSnapshot snap = trainingSnapshots.get(sourceBotName);
        if (snap != null) {
            for (Map.Entry<String, Integer> entry : snap.successCounts.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    targetTm.recordSuccess(entry.getKey());
                }
            }
            LOGGER.info("Loaded training data from bot {} into target trainer", sourceBotName);
        }
    }

    /**
     * Get all registered locations.
     */
    public Collection<WorldLocation> getAllLocations() {
        return locations.values();
    }

    /**
     * Get all training snapshots (for inspection/merging).
     */
    public Map<String, TrainingSnapshot> getAllTrainingSnapshots() {
        return new HashMap<>(trainingSnapshots);
    }

    /**
     * Persist to NBT (called on server shutdown).
     */
    public CompoundTag serializeToNBT() {
        CompoundTag root = new CompoundTag();

        // Save locations
        ListTag locList = new ListTag();
        for (WorldLocation loc : locations.values()) {
            CompoundTag locTag = new CompoundTag();
            locTag.putString("name", loc.name);
            locTag.putInt("x", loc.x);
            locTag.putInt("y", loc.y);
            locTag.putInt("z", loc.z);
            locTag.putString("type", loc.type);
            locTag.putString("discoveredBy", loc.discoveredBy);
            locTag.putLong("lastUpdated", loc.lastUpdated);
            locList.add(locTag);
        }
        root.put("locations", locList);

        // Save training snapshots
        ListTag trainingList = new ListTag();
        for (TrainingSnapshot snap : trainingSnapshots.values()) {
            CompoundTag snapTag = new CompoundTag();
            snapTag.putString("botName", snap.botName);
            snapTag.putLong("timestamp", snap.timestamp);
            CompoundTag successTag = new CompoundTag();
            for (Map.Entry<String, Integer> e : snap.successCounts.entrySet()) {
                successTag.putInt(e.getKey(), e.getValue());
            }
            snapTag.put("successes", successTag);
            trainingList.add(snapTag);
        }
        root.put("trainingSnapshots", trainingList);

        return root;
    }

    /**
     * Load from NBT (called on server startup).
     */
    public void deserializeFromNBT(CompoundTag root) {
        if (!root.contains("locations", Tag.TAG_LIST)) return;

        ListTag locList = root.getList("locations", Tag.TAG_COMPOUND);
        for (int i = 0; i < locList.size(); i++) {
            CompoundTag locTag = locList.getCompound(i);
            registerLocation(
                locTag.getString("name"),
                locTag.getInt("x"),
                locTag.getInt("y"),
                locTag.getInt("z"),
                locTag.getString("type"),
                locTag.getString("discoveredBy")
            );
        }

        ListTag trainingList = root.getList("trainingSnapshots", Tag.TAG_COMPOUND);
        for (int i = 0; i < trainingList.size(); i++) {
            CompoundTag snapTag = trainingList.getCompound(i);
            TrainingSnapshot snap = new TrainingSnapshot(snapTag.getString("botName"));
            CompoundTag successTag = snapTag.getCompound("successes");
            for (String key : successTag.getAllKeys()) {
                snap.successCounts.put(key, successTag.getInt(key));
            }
            trainingSnapshots.put(snap.botName, snap);
        }

        LOGGER.info("Loaded {} locations and {} training snapshots from NBT", locations.size(), trainingSnapshots.size());
    }
}
