package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.AbstractHorse;
import net.minecraft.world.entity.animal.Llama;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Animal Herding Utilities: Lead animals to locations.
 * - Lead animals toward player
 * - Navigate herds
 * - Handle unruly animals (llamas)
 * - Path finding for herds
 */
public class AnimalHerdingUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-herding");

    public static class HerdInfo {
        public List<Animal> animals;
        public BlockPos targetLocation;
        public BlockPos currentCenter;
        public int speed;  // 1-5 (1=slow, 5=fast)
        public boolean isMoving;

        public HerdInfo(List<Animal> animals, BlockPos target) {
            this.animals = new ArrayList<>(animals);
            this.targetLocation = target;
            this.currentCenter = animals.get(0).blockPosition();
            this.speed = 2;  // Medium speed
            this.isMoving = false;
        }

        public double distanceToTarget() {
            return currentCenter.distToCenterSqrt(targetLocation);
        }

        public int getHerdSize() {
            return (int) animals.stream().filter(Animal::isAlive).count();
        }
    }

    /**
     * Lead single animal to location using food.
     */
    public static boolean leadAnimalToLocation(ServerPlayer player, Animal animal, BlockPos target, ItemStack food) {
        if (animal == null || !animal.isAlive()) {
            return false;
        }

        try {
            // Lure animal toward target using food
            double dx = target.getX() - animal.getX();
            double dz = target.getZ() - animal.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            if (distance < 2.0) {
                return true;  // Reached target
            }

            // Hold food to lure animal
            // In real implementation, would move player toward target while holding food
            LOGGER.debug("Leading {} toward {}", animal.getType().toString(), target.toShortString());
            
            return false;  // In progress
        } catch (Exception e) {
            LOGGER.debug("Error leading animal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Herd multiple animals to location.
     */
    public static HerdInfo createHerd(List<Animal> animals, BlockPos target) {
        HerdInfo herd = new HerdInfo(animals, target);
        LOGGER.info("Created herd of {} animals heading to {}", 
            animals.size(), target.toShortString());
        return herd;
    }

    /**
     * Move herd toward target.
     */
    public static void moveHerd(HerdInfo herd, ServerPlayer herder) {
        if (herd == null || !herd.isMoving) return;

        // Keep herd together
        BlockPos herdCenter = getHerdCenter(herd.animals);
        
        double dx = herd.targetLocation.getX() - herdCenter.getX();
        double dz = herd.targetLocation.getZ() - herdCenter.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < 2.0) {
            herd.isMoving = false;
            LOGGER.info("Herd reached target");
            return;
        }

        // Move herder toward herd center to push animals forward
        // In real implementation: move player, hold food, use herding stick

        LOGGER.debug("Herd progress: {}/{} blocks", 
            (int) (distance), (int) herd.distanceToTarget());
    }

    /**
     * Get center position of herd.
     */
    private static BlockPos getHerdCenter(List<Animal> animals) {
        if (animals.isEmpty()) return BlockPos.ZERO;

        double sumX = 0, sumZ = 0;
        for (Animal animal : animals) {
            if (animal.isAlive()) {
                sumX += animal.getX();
                sumZ += animal.getZ();
            }
        }

        int count = (int) animals.stream().filter(Animal::isAlive).count();
        return new BlockPos((int) (sumX / count), 64, (int) (sumZ / count));
    }

    /**
     * Lead animal using rope/lead (if applicable).
     */
    public static boolean leadAnimalWithRope(ServerPlayer player, Animal animal, BlockPos target) {
        if (!(animal instanceof AbstractHorse)) {
            LOGGER.warn("Can only lead horses with rope");
            return false;
        }

        AbstractHorse horse = (AbstractHorse) animal;
        
        // In real implementation: attach lead, move toward target
        try {
            // Check player has a lead item
            boolean hasLead = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() == Items.LEAD) { hasLead = true; break; }
            }
            if (!hasLead) {
                LOGGER.warn("Player has no lead item; cannot rope horse");
                return false;
            }

            // Simulate attaching and moving
            LOGGER.info("Leading horse to {} (best-effort)", target.toShortString());
            return true;
        } catch (Exception e) {
            LOGGER.debug("leadAnimalWithRope error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Handle unruly animals (llamas spit, etc.).
     */
    public static void handleUnrulyAnimal(Animal animal, BlockPos targetWaypoint) {
        if (animal instanceof Llama) {
            Llama llama = (Llama) animal;
            // Llamas spit when upset - redirect to waypoint to calm down
            LOGGER.info("Llama is upset, redirecting to waypoint");
        }
    }

    /**
     * Get best lure food for animal.
     */
    public static ItemStack getLureFood(Animal animal) {
        String type = animal.getType().toString().toLowerCase();
        
        if (type.contains("sheep")) return new ItemStack(Items.WHEAT);
        if (type.contains("cow") || type.contains("mooshroom")) return new ItemStack(Items.WHEAT);
        if (type.contains("pig")) return new ItemStack(Items.CARROT);
        if (type.contains("chicken")) return new ItemStack(Items.SEEDS);
        if (type.contains("horse")) return new ItemStack(Items.GOLDEN_CARROT);
        if (type.contains("rabbit")) return new ItemStack(Items.CARROT);
        if (type.contains("goat")) return new ItemStack(Items.WHEAT);
        if (type.contains("llama")) return new ItemStack(Items.HAY_BLOCK);
        if (type.contains("bee")) return new ItemStack(Items.FLOWER);
        
        return new ItemStack(Items.WHEAT);  // Default
    }

    /**
     * Estimate time to reach target with herd.
     */
    public static long estimateTimeToTarget(HerdInfo herd) {
        // Rough estimate: animals move at ~4.3 blocks/second
        // Average herding: ~3 blocks/second
        double distance = herd.distanceToTarget();
        double speedMultiplier = herd.speed / 3.0;
        
        long secondsNeeded = (long) (distance / (3.0 * speedMultiplier));
        return secondsNeeded;
    }

    /**
     * Split herd (leave some, take some).
     */
    public static List<Animal> splitHerd(HerdInfo herd, int count) {
        List<Animal> taken = new ArrayList<>();
        
        for (int i = 0; i < count && i < herd.animals.size(); i++) {
            if (herd.animals.get(i).isAlive()) {
                taken.add(herd.animals.get(i));
            }
        }

        LOGGER.info("Split herd: took {}, left {}", taken.size(), herd.getHerdSize() - taken.size());
        return taken;
    }

    /**
     * Merge herds.
     */
    public static HerdInfo mergeHerds(HerdInfo herd1, HerdInfo herd2, BlockPos newTarget) {
        List<Animal> merged = new ArrayList<>(herd1.animals);
        merged.addAll(herd2.animals);
        
        HerdInfo newHerd = new HerdInfo(merged, newTarget);
        LOGGER.info("Merged herds: {} total animals", newHerd.getHerdSize());
        return newHerd;
    }
}
