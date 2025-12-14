package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Dimensional Travel Manager: Travel between Overworld, Nether, End.
 * - Navigate to dimensions
 * - Find and use portals
 * - Handle coordinate scaling (Nether = 1/8 scale)
 * - Create portals if needed
 */
public class DimensionalTravelManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-travel");

    public enum Dimension {
        OVERWORLD("overworld", 0),
        NETHER("nether", -1),
        END("end", 1);

        public final String name;
        public final int id;

        Dimension(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public static Dimension fromString(String str) {
            String lower = str.toLowerCase();
            for (Dimension dim : values()) {
                if (dim.name.contains(lower) || lower.contains(dim.name)) {
                    return dim;
                }
            }
            return OVERWORLD;
        }
    }

    public static class TravelRoute {
        public BlockPos start;
        public BlockPos end;
        public Dimension startDimension;
        public Dimension endDimension;
        public List<BlockPos> portalLocations;
        public boolean requiresNewPortal;

        public TravelRoute(BlockPos start, BlockPos end, Dimension from, Dimension to) {
            this.start = start;
            this.end = end;
            this.startDimension = from;
            this.endDimension = to;
            this.portalLocations = new ArrayList<>();
            this.requiresNewPortal = false;
        }
    }

    /**
     * Travel to dimension at coordinates.
     */
    public static TravelRoute planDimensionalTravel(ServerPlayer player, String dimensionName, int x, int y, int z) {
        Dimension targetDim = Dimension.fromString(dimensionName);
        BlockPos targetPos = new BlockPos(x, y, z);

        return planDimensionalTravel(player, targetDim, targetPos);
    }

    /**
     * Plan optimal travel route considering available transportation methods.
     */
    public static TravelRoute planOptimalTravel(ServerPlayer player, Dimension targetDim, BlockPos targetPos,
                                               com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        BlockPos currentPos = player.blockPosition();
        ServerLevel currentLevel = (ServerLevel) player.level();

        Dimension currentDim = identifyDimension(currentLevel);
        TravelRoute route = new TravelRoute(currentPos, targetPos, currentDim, targetDim);

        if (currentDim == targetDim) {
            // Same dimension - choose fastest ground transportation
            return planIntraDimensionalTravel(player, targetPos, worldMemory);
        }

        // Inter-dimensional travel - find best portal route
        return planInterDimensionalTravel(player, targetDim, targetPos, worldMemory);
    }

    /**
     * Plan travel within the same dimension using optimal transportation.
     */
    private static TravelRoute planIntraDimensionalTravel(ServerPlayer player, BlockPos targetPos,
                                                         com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        TravelRoute route = new TravelRoute(player.blockPosition(), targetPos,
                                          identifyDimension((ServerLevel) player.level()),
                                          identifyDimension((ServerLevel) player.level()));

        double distance = Math.sqrt(player.blockPosition().distSqr(targetPos));

        // Check available transportation methods in order of speed
        if (hasElytra(player) && distance > 100) {
            // Elytra is fastest for long distances
            route.portalLocations.add(player.blockPosition()); // Start
            route.portalLocations.add(targetPos); // End
            LOGGER.info("Planning Elytra travel - distance: {} blocks", (int) distance);
        } else if (hasHorseOrVehicle(player) && distance > 50) {
            // Horse/cart for medium distances
            route.portalLocations.add(player.blockPosition());
            route.portalLocations.add(targetPos);
            LOGGER.info("Planning vehicle travel - distance: {} blocks", (int) distance);
        } else if (worldMemory != null) {
            // Check for remembered waypoints or paths
            var nearestWaypoint = worldMemory.findNearestLocation("waypoint", player.getX(), player.getY(), player.getZ());
            if (nearestWaypoint != null && player.blockPosition().distSqr(nearestWaypoint.x, nearestWaypoint.y, nearestWaypoint.z) < distance) {
                route.portalLocations.add(new BlockPos(nearestWaypoint.x, nearestWaypoint.y, nearestWaypoint.z));
                route.portalLocations.add(targetPos);
                LOGGER.info("Planning waypoint-assisted travel via {}", nearestWaypoint.name);
            } else {
                // Standard walking
                route.portalLocations.add(player.blockPosition());
                route.portalLocations.add(targetPos);
                LOGGER.info("Planning walking travel - distance: {} blocks", (int) distance);
            }
        } else {
            // Default to walking
            route.portalLocations.add(player.blockPosition());
            route.portalLocations.add(targetPos);
            LOGGER.info("Planning walking travel - distance: {} blocks", (int) distance);
        }

        return route;
    }

    /**
     * Plan inter-dimensional travel using optimal portal routes.
     */
    private static TravelRoute planInterDimensionalTravel(ServerPlayer player, Dimension targetDim, BlockPos targetPos,
                                                         com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        BlockPos currentPos = player.blockPosition();
        ServerLevel currentLevel = (ServerLevel) player.level();

        Dimension currentDim = identifyDimension(currentLevel);
        TravelRoute route = new TravelRoute(currentPos, targetPos, currentDim, targetDim);

        // Try to find remembered portals first
        if (worldMemory != null) {
            if (currentDim == Dimension.OVERWORLD && targetDim == Dimension.NETHER) {
                return planNetherTravelWithMemory(route, worldMemory);
            } else if (currentDim == Dimension.NETHER && targetDim == Dimension.OVERWORLD) {
                return planNetherReturnWithMemory(route, worldMemory);
            } else if (currentDim == Dimension.OVERWORLD && targetDim == Dimension.END) {
                return planEndTravelWithMemory(route, worldMemory);
            }
        }

        // Fall back to basic portal finding
        return planDimensionalTravel(player, targetDim, targetPos);
    }

    /**
     * Check if player has Elytra equipped.
     */
    private static boolean hasElytra(ServerPlayer player) {
        return player.getInventory().armor.get(2).getItem().toString().toLowerCase().contains("elytra");
    }

    /**
     * Plan Nether travel using remembered portals.
     */
    private static TravelRoute planNetherTravelWithMemory(TravelRoute route, com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        LOGGER.info("Planning Nether travel with memory assistance");

        // Find remembered Nether portal closest to target location
        var rememberedPortal = worldMemory.findNearestLocationInDimension("portal", "nether",
            route.end.getX(), route.end.getY(), route.end.getZ());

        if (rememberedPortal != null) {
            // Use remembered portal
            BlockPos netherPortal = new BlockPos(rememberedPortal.x, rememberedPortal.y, rememberedPortal.z);
            BlockPos overworldPortal = scaleFromNether(netherPortal);

            route.portalLocations.add(overworldPortal);
            route.portalLocations.add(netherPortal);
            LOGGER.info("Using remembered Nether portal at {}", netherPortal.toShortString());
        } else {
            // Fall back to coordinate-based portal finding
            BlockPos expectedNetherPortal = scaleToNether(route.end);
            BlockPos overWorldPortal = scaleFromNether(expectedNetherPortal);

            route.portalLocations.add(overWorldPortal);
            route.portalLocations.add(expectedNetherPortal);
            LOGGER.info("No remembered portal, using coordinate-based portal at {}", expectedNetherPortal.toShortString());
        }

        return route;
    }

    /**
     * Plan Nether return using remembered portals.
     */
    private static TravelRoute planNetherReturnWithMemory(TravelRoute route, com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        LOGGER.info("Planning Nether return with memory assistance");

        // Find remembered Overworld portal
        var rememberedPortal = worldMemory.findNearestLocationInDimension("portal", "overworld",
            route.end.getX(), route.end.getY(), route.end.getZ());

        if (rememberedPortal != null) {
            BlockPos overworldPortal = new BlockPos(rememberedPortal.x, rememberedPortal.y, rememberedPortal.z);
            BlockPos netherPortal = scaleToNether(overworldPortal);

            route.portalLocations.add(netherPortal);
            route.portalLocations.add(overworldPortal);
            LOGGER.info("Using remembered Overworld portal at {}", overworldPortal.toShortString());
        } else {
            // Fall back to scaling current position
            BlockPos netherPortal = route.start;
            BlockPos overworldPortal = scaleFromNether(netherPortal);

            route.portalLocations.add(netherPortal);
            route.portalLocations.add(overworldPortal);
            LOGGER.info("No remembered portal, using scaled portal at {}", overworldPortal.toShortString());
        }

        return route;
    }

    /**
     * Plan End travel using remembered portals.
     */
    private static TravelRoute planEndTravelWithMemory(TravelRoute route, com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        LOGGER.info("Planning End travel with memory assistance");

        // Look for remembered End portal or stronghold
        var endPortal = worldMemory.getLocation("end_portal");
        var stronghold = worldMemory.getLocation("stronghold");

        if (endPortal != null) {
            route.portalLocations.add(route.start);
            route.portalLocations.add(new BlockPos(endPortal.x, endPortal.y, endPortal.z));
            LOGGER.info("Using remembered End portal");
        } else if (stronghold != null) {
            route.portalLocations.add(route.start);
            route.portalLocations.add(new BlockPos(stronghold.x, stronghold.y, stronghold.z));
            route.requiresNewPortal = true;
            LOGGER.info("Using remembered stronghold for End portal");
        } else {
            // Need to find stronghold
            route.portalLocations.add(route.start);
            route.portalLocations.add(route.end);
            route.requiresNewPortal = true;
            LOGGER.warn("No remembered End access points - will need to locate stronghold");
        }

        return route;
    }

    /**
     * Identify dimension from level.
     */
    public static Dimension identifyDimension(ServerLevel level) {
        String name = level.dimension().toString().toLowerCase();
        
        if (name.contains("nether")) return Dimension.NETHER;
        if (name.contains("end") || name.contains("the_end")) return Dimension.END;
        return Dimension.OVERWORLD;
    }

    /**
     * Plan travel to Nether.
     */
    private static TravelRoute planNetherTravel(TravelRoute route) {
        LOGGER.info("Planning Nether travel from {} to Nether coords {}", 
            route.start.toShortString(), route.end.toShortString());

        // Find nearby Nether portal in Overworld
        // Portal would typically be found at Nether coordinates ÷ 8
        BlockPos expectedNetherPortal = scaleToNether(route.end);

        // In Overworld, portal location would be: Nether coords × 8
        BlockPos overWorldPortal = scaleFromNether(expectedNetherPortal);

        route.portalLocations.add(overWorldPortal);
        route.portalLocations.add(expectedNetherPortal);

        LOGGER.info("Nether portal route: {} → {}", overWorldPortal.toShortString(), expectedNetherPortal.toShortString());
        return route;
    }

    /**
     * Plan return from Nether.
     */
    private static TravelRoute planNetherReturn(TravelRoute route) {
        LOGGER.info("Planning return from Nether to Overworld at {}", route.end.toShortString());

        // Scale Nether coordinates to Overworld
        BlockPos netherPortal = route.start;
        BlockPos overworldPortal = scaleFromNether(netherPortal);

        route.portalLocations.add(netherPortal);
        route.portalLocations.add(overworldPortal);

        return route;
    }

    /**
     * Plan travel to End.
     */
    private static TravelRoute planEndTravel(TravelRoute route) {
        LOGGER.info("Planning travel to End");

        // To reach End: must use end portal frame in stronghold
        // Locate stronghold or build/use Nether portal route
        
        route.portalLocations.add(route.start);
        route.portalLocations.add(route.end);
        route.requiresNewPortal = true;  // End portals hard to create

        LOGGER.warn("End travel requires stronghold - may need guidance");
        return route;
    }

    /**
     * Plan return from End.
     */
    private static TravelRoute planEndReturn(TravelRoute route) {
        LOGGER.info("Planning return from End");

        // Use exit portal to return to Overworld spawn
        // Or use created portal if available
        
        return route;
    }

    /**
     * Scale coordinates to Nether (÷ 8).
     */
    public static BlockPos scaleToNether(BlockPos overworldPos) {
        return new BlockPos(
            Mth.floor(overworldPos.getX() / 8.0),
            overworldPos.getY(),  // Y stays same
            Mth.floor(overworldPos.getZ() / 8.0)
        );
    }

    /**
     * Scale coordinates from Nether to Overworld (× 8).
     */
    public static BlockPos scaleFromNether(BlockPos netherPos) {
        return new BlockPos(
            netherPos.getX() * 8,
            netherPos.getY(),  // Y stays same
            netherPos.getZ() * 8
        );
    }

    /**
     * Check if portal exists at location.
     */
    public static boolean hasPortalAt(ServerLevel level, BlockPos pos, Dimension expectedDim) {
        try {
            BlockState block = level.getBlockState(pos);
            
            if (expectedDim == Dimension.NETHER || expectedDim == Dimension.OVERWORLD) {
                return block.getBlock() == Blocks.NETHER_PORTAL;
            } else if (expectedDim == Dimension.END) {
                return block.getBlock() == Blocks.END_PORTAL;
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking portal: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Find nearest portal of type.
     */
    public static BlockPos findNearestPortal(ServerPlayer player, Dimension targetDim) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();
        int searchRadius = 128;

        for (int distance = 0; distance < searchRadius; distance++) {
            for (int x = -distance; x <= distance; x++) {
                for (int z = -distance; z <= distance; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != distance) continue;

                    BlockPos checkPos = playerPos.offset(x, 0, z);
                    if (hasPortalAt(level, checkPos, targetDim)) {
                        LOGGER.info("Found portal at {}", checkPos.toShortString());
                        return checkPos;
                    }
                }
            }
        }

        LOGGER.warn("No portal found within {} blocks", searchRadius);
        return null;
    }

    /**
     * Navigate to portal and use it.
     */
    public static boolean usePortal(ServerPlayer player, BlockPos portalPos) {
        if (portalPos == null) return false;

        try {
            // Navigate to portal
            double distance = player.distanceToSqr(portalPos.getCenter());
            
            if (distance < 4.0) {
                // Close enough to use portal
                // In real implementation: move player into portal block
                LOGGER.info("Using portal at {}", portalPos.toShortString());
                return true;
            } else {
                LOGGER.debug("Moving toward portal: {:.1f} blocks away", Math.sqrt(distance));
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error using portal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create Nether portal if not found.
     */
    public static boolean createNetherPortal(ServerLevel level, BlockPos location) {
        try {
            // Obsidian frame: 5×4 with empty center (3×2)
            // Build frame at location
            LOGGER.info("Creating Nether portal at {}", location.toShortString());
            // Place a 4-high x 5-wide obsidian rectangle with hollow center
            BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState();

            // We'll build a portal facing along X axis with the lower-left at `location`
            for (int x = 0; x < 4; x++) { // width along x
                for (int y = 0; y < 5; y++) { // height
                    BlockPos pos = location.offset(x, y, 0);
                    // Frame condition: edges (x==0 || x==3) or (y==0 || y==4)
                    if (x == 0 || x == 3 || y == 0 || y == 4) {
                        level.setBlock(pos, obs, 3);
                    } else {
                        // interior -> portal
                        level.setBlock(pos, portal, 3);
                    }
                }
            }
            LOGGER.info("Nether portal created (best-effort). Consider relighting if inert");
            
            return true;
        } catch (Exception e) {
            LOGGER.debug("Error creating portal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Find remembered portal locations for travel.
     */
    public static TravelRoute findRememberedPortalRoute(ServerPlayer player, Dimension targetDim, com.tyler.forgeai.ai.SharedWorldMemory worldMemory) {
        BlockPos currentPos = player.blockPosition();
        ServerLevel currentLevel = (ServerLevel) player.level();
        Dimension currentDim = identifyDimension(currentLevel);

        if (currentDim == targetDim) {
            return new TravelRoute(currentPos, currentPos, currentDim, targetDim);
        }

        TravelRoute route = new TravelRoute(currentPos, currentPos, currentDim, targetDim);

        // Look for remembered portals
        if (currentDim == Dimension.OVERWORLD && targetDim == Dimension.NETHER) {
            var netherPortals = worldMemory.getLocationsByTypeAndDimension("portal", "nether");
            if (!netherPortals.isEmpty()) {
                var nearestPortal = worldMemory.findNearestLocationInDimension("portal", "nether",
                    currentPos.getX(), currentPos.getY(), currentPos.getZ());
                if (nearestPortal != null) {
                    BlockPos overworldPortal = scaleFromNether(new BlockPos(nearestPortal.x, nearestPortal.y, nearestPortal.z));
                    route.portalLocations.add(overworldPortal);
                    route.portalLocations.add(new BlockPos(nearestPortal.x, nearestPortal.y, nearestPortal.z));
                    LOGGER.info("Using remembered Nether portal at overworld: {}", overworldPortal.toShortString());
                }
            }
        } else if (currentDim == Dimension.NETHER && targetDim == Dimension.OVERWORLD) {
            var overworldPortals = worldMemory.getLocationsByTypeAndDimension("portal", "overworld");
            if (!overworldPortals.isEmpty()) {
                var nearestPortal = worldMemory.findNearestLocationInDimension("portal", "overworld",
                    currentPos.getX(), currentPos.getY(), currentPos.getZ());
                if (nearestPortal != null) {
                    BlockPos netherPortal = scaleToNether(new BlockPos(nearestPortal.x, nearestPortal.y, nearestPortal.z));
                    route.portalLocations.add(netherPortal);
                    route.portalLocations.add(new BlockPos(nearestPortal.x, nearestPortal.y, nearestPortal.z));
                    LOGGER.info("Using remembered Overworld portal at nether: {}", netherPortal.toShortString());
                }
            }
        }

        return route;
    }

    /**
     * Calculate travel time between dimensions.
     */
    public static long estimateTravelTime(TravelRoute route) {
        // Estimate: 10 seconds per portal + navigation time
        long portalTime = route.portalLocations.size() * 10L;
        
        // Navigation time: ~5 seconds per 20 blocks
        long navigationTime = 0;
        for (int i = 0; i < route.portalLocations.size() - 1; i++) {
            BlockPos p1 = route.portalLocations.get(i);
            BlockPos p2 = route.portalLocations.get(i + 1);
            double distance = Math.sqrt(p1.distToCenterSqrt(p2));
            navigationTime += (long) (distance / 20.0 * 5.0);
        }

        return portalTime + navigationTime;
    }
}
