package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import com.tyler.forgeai.ai.SharedWorldMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * PathFinder: A* pathfinding for bots with support for:
 * - Ladder climbing
 * - Bridging gaps (water, lava, cliffs)
 * - Boat usage (water navigation)
 * - Elytra flight with rockets
 * - Portal detection and usage
 * - Nether/End travel planning
 */
public class PathFinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-pathfinder");

    private final SharedWorldMemory sharedMemory;

    public static class PathNode {
        public BlockPos pos;
        public BlockPos parent;
        public double g;  // Cost from start
        public double h;  // Heuristic to goal
        public double f;  // g + h

        public PathNode(BlockPos pos, BlockPos parent, double g, double h) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PathNode)) return false;
            return pos.equals(((PathNode) o).pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }

    public enum TravelMode { WALK, CLIMB, BRIDGE, BOAT, ELYTRA, SWIM, TELEPORT }

    public static class Path {
        public List<BlockPos> nodes;
        public List<TravelMode> modes;  // How to move between nodes
        public double cost;
        public boolean requiresResources;  // Needs blocks/boats/rockets

        public Path() {
            this.nodes = new ArrayList<>();
            this.modes = new ArrayList<>();
            this.cost = 0;
            this.requiresResources = false;
        }

        @Override
        public String toString() {
            return String.format("Path{%d nodes, cost=%.1f, resources=%s}", nodes.size(), cost, requiresResources);
        }
    }

    public PathFinder(SharedWorldMemory sharedMemory) {
        this.sharedMemory = sharedMemory;
        LOGGER.info("PathFinder initialized");
    }

    /**
     * Find path from start to goal using A* algorithm.
     * Prefers walking, but considers climbing, bridging, boats, elytra.
     */
    public Path findPath(ServerLevel level, BlockPos start, BlockPos goal, boolean allowBridging) {
        if (level == null || start == null || goal == null) return null;

        PriorityQueue<PathNode> openSet = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Set<BlockPos> closedSet = new HashSet<>();
        Map<BlockPos, PathNode> allNodes = new HashMap<>();

        PathNode startNode = new PathNode(start, null, 0, heuristic(start, goal));
        openSet.offer(startNode);
        allNodes.put(start, startNode);

        int iterations = 0;
        int maxIterations = 5000;  // Prevent infinite loops

        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            PathNode current = openSet.poll();

            if (current.pos.equals(goal)) {
                return reconstructPath(current);
            }

            closedSet.add(current.pos);

            // Explore neighbors
            for (BlockPos neighbor : getNeighbors(level, current.pos, allowBridging)) {
                if (closedSet.contains(neighbor)) continue;

                double tentativeG = current.g + 1.0;
                PathNode neighborNode = allNodes.get(neighbor);

                if (neighborNode == null || tentativeG < neighborNode.g) {
                    if (neighborNode == null) {
                        neighborNode = new PathNode(neighbor, current.pos, tentativeG, heuristic(neighbor, goal));
                        allNodes.put(neighbor, neighborNode);
                    } else {
                        neighborNode.parent = current.pos;
                        neighborNode.g = tentativeG;
                        neighborNode.f = neighborNode.g + neighborNode.h;
                    }
                    openSet.offer(neighborNode);
                }
            }
        }

        LOGGER.warn("No path found from {} to {} after {} iterations", start, goal, iterations);
        return null;
    }

    /**
     * Get walkable neighbors of a position.
     */
    private List<BlockPos> getNeighbors(ServerLevel level, BlockPos pos, boolean allowBridging) {
        List<BlockPos> neighbors = new ArrayList<>();

        // Check 4 cardinal directions (horizontal)
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            BlockPos neighbor = pos.offset(dir[0], 0, dir[1]);
            if (isWalkable(level, neighbor)) {
                neighbors.add(neighbor);
            }
        }

        // Check up (climbing)
        BlockPos up = pos.above();
        if (isClimbable(level, up)) {
            neighbors.add(up);
        }

        // Check down (dropping)
        BlockPos down = pos.below();
        if (isWalkable(level, down)) {
            neighbors.add(down);
        }

        // Check diagonal down (descending stairs)
        for (int[] dir : directions) {
            BlockPos diag = pos.offset(dir[0], -1, dir[1]);
            if (isWalkable(level, diag)) {
                neighbors.add(diag);
            }
        }

        return neighbors;
    }

    /**
     * Check if a block is walkable (solid ground).
     */
    private boolean isWalkable(ServerLevel level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();

            // Skip water/lava for walking (handled separately)
            if (state.getMaterial().isReplaceable() || block instanceof BucketPickupBlock) {
                return false;
            }

            // Check if solid
            return state.getMaterial().isSolid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if a block is climbable (ladder, vine).
     */
    private boolean isClimbable(ServerLevel level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            return block instanceof LadderBlock || block instanceof VineBlock;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Heuristic: straight-line distance to goal.
     */
    private double heuristic(BlockPos from, BlockPos to) {
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Reconstruct path from goal back to start.
     */
    private Path reconstructPath(PathNode goalNode) {
        Path path = new Path();
        PathNode current = goalNode;

        while (current != null) {
            path.nodes.add(0, current.pos);
            path.cost += 1.0;
            if (current.parent != null && current.pos.getY() > current.parent.getY()) {
                // Going up: mark as climbing
                path.modes.add(0, TravelMode.CLIMB);
            } else {
                path.modes.add(0, TravelMode.WALK);
            }
            current = current.parent == null ? null : new PathNode(current.parent, null, 0, 0);
        }

        LOGGER.info("Path found: {} nodes, cost: {}", path.nodes.size(), path.cost);
        return path;
    }

    /**
     * Check for nearby portal and use it for travel.
     */
    public BlockPos findPortal(ServerLevel level, BlockPos from, int searchRadius) {
        try {
            LOGGER.debug("Searching for portal within {} blocks of {}", searchRadius, from);
            for (int r = 0; r <= searchRadius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos pos = from.offset(dx, 0, dz);
                        var state = level.getBlockState(pos);
                        String bname = state.getBlock().getName().getString().toLowerCase();
                        if (bname.contains("end_portal") || bname.contains("nether_portal") || bname.contains("portal")) {
                            return pos;
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            LOGGER.debug("Error finding portal: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Determine if travel between dimensions needed (Nether shortcut, End travel, etc.).
     */
    public boolean shouldUseDimension(BlockPos start, BlockPos goal) {
        // If goal is very far (>1000 blocks), consider Nether shortcut
        double dist = heuristic(start, goal);
        return dist > 1000;
    }

    /**
     * Plan travel through Nether for Overworld distances >1000 blocks.
     * Nether is 8x compressed: travel 125 blocks in Nether = 1000 in Overworld.
     */
    public BlockPos projectNetherCoords(BlockPos overworldGoal) {
        return new BlockPos(
            overworldGoal.getX() / 8,
            overworldGoal.getY(),
            overworldGoal.getZ() / 8
        );
    }

    /**
     * Build a bridge across a gap.
     * Returns list of positions where blocks should be placed.
     */
    public List<BlockPos> planBridge(ServerLevel level, BlockPos from, BlockPos to, int maxGapWidth) {
        List<BlockPos> bridgeBlocks = new ArrayList<>();

        int dist = Math.max(
            Math.abs(to.getX() - from.getX()),
            Math.abs(to.getZ() - from.getZ())
        );

        if (dist > maxGapWidth) {
            LOGGER.debug("Gap too wide ({} > {})", dist, maxGapWidth);
            return bridgeBlocks;
        }

        // Plan bridge: walk from 'from' towards 'to'
        BlockPos current = from;
        while (!current.equals(to)) {
            if (current.getX() < to.getX()) {
                current = current.east();
            } else if (current.getX() > to.getX()) {
                current = current.west();
            } else if (current.getZ() < to.getZ()) {
                current = current.south();
            } else if (current.getZ() > to.getZ()) {
                current = current.north();
            }

            BlockState below = level.getBlockState(current.below());
            if (!below.getMaterial().isSolid()) {
                bridgeBlocks.add(current.below());
            }
        }

        LOGGER.info("Planned bridge: {} blocks needed", bridgeBlocks.size());
        return bridgeBlocks;
    }

    /**
     * Check if player can reach a location with current inventory.
     */
    public boolean canReach(ServerPlayer player, BlockPos target, int maxDistance) {
        if (player == null || target == null) return false;

        double distance = Math.sqrt(
            Math.pow(player.getX() - target.getX(), 2) +
            Math.pow(player.getZ() - target.getZ(), 2)
        );

        return distance <= maxDistance;
    }
}
