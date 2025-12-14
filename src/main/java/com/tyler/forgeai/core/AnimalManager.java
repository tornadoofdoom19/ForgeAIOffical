package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * AnimalManager: Handles animal breeding, farming, and management.
 * Learns breeding requirements and optimizes animal production.
 */
public class AnimalManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-animal-manager");

    private final Map<String, AnimalBreedingInfo> breedingKnowledge = new HashMap<>();
    private boolean farmingMode = false;

    public static class AnimalBreedingInfo {
        public String animalType;
        public String foodItem;
        public int minAge = -24000; // Baby age
        public boolean canBreed = true;
        public long lastBred = 0;
        public int breedingCooldown = 6000; // 5 minutes in ticks

        public AnimalBreedingInfo(String animalType, String foodItem) {
            this.animalType = animalType;
            this.foodItem = foodItem;
        }

        public boolean canBreedNow() {
            return canBreed && (System.currentTimeMillis() - lastBred) > (breedingCooldown * 50); // Convert ticks to ms
        }
    }

    public AnimalManager() {
        initializeBreedingKnowledge();
        LOGGER.info("AnimalManager initialized with breeding knowledge for {} animal types", breedingKnowledge.size());
    }

    private void initializeBreedingKnowledge() {
        // Initialize breeding requirements for all Minecraft animals
        breedingKnowledge.put("cow", new AnimalBreedingInfo("cow", "wheat"));
        breedingKnowledge.put("sheep", new AnimalBreedingInfo("sheep", "wheat"));
        breedingKnowledge.put("pig", new AnimalBreedingInfo("pig", "carrot"));
        breedingKnowledge.put("chicken", new AnimalBreedingInfo("chicken", "wheat_seeds"));
        breedingKnowledge.put("horse", new AnimalBreedingInfo("horse", "golden_apple"));
        breedingKnowledge.put("donkey", new AnimalBreedingInfo("donkey", "golden_apple"));
        breedingKnowledge.put("mule", new AnimalBreedingInfo("mule", "golden_apple"));
        breedingKnowledge.put("llama", new AnimalBreedingInfo("llama", "hay_block"));
        breedingKnowledge.put("rabbit", new AnimalBreedingInfo("rabbit", "carrot"));
        breedingKnowledge.put("wolf", new AnimalBreedingInfo("wolf", "bone"));
        breedingKnowledge.put("cat", new AnimalBreedingInfo("cat", "fish"));
        breedingKnowledge.put("ocelot", new AnimalBreedingInfo("ocelot", "fish"));
        breedingKnowledge.put("parrot", new AnimalBreedingInfo("parrot", "wheat_seeds"));
        breedingKnowledge.put("turtle", new AnimalBreedingInfo("turtle", "seagrass"));
        breedingKnowledge.put("panda", new AnimalBreedingInfo("panda", "bamboo"));
        breedingKnowledge.put("fox", new AnimalBreedingInfo("fox", "sweet_berries"));
        breedingKnowledge.put("bee", new AnimalBreedingInfo("bee", "dandelion"));
        breedingKnowledge.put("hoglin", new AnimalBreedingInfo("hoglin", "crimson_fungus"));
        breedingKnowledge.put("strider", new AnimalBreedingInfo("strider", "warped_fungus"));
        breedingKnowledge.put("axolotl", new AnimalBreedingInfo("axolotl", "tropical_fish"));
        breedingKnowledge.put("goat", new AnimalBreedingInfo("goat", "wheat"));
        breedingKnowledge.put("frog", new AnimalBreedingInfo("frog", "slime_ball"));
        breedingKnowledge.put("camel", new AnimalBreedingInfo("camel", "cactus"));
        breedingKnowledge.put("sniffer", new AnimalBreedingInfo("sniffer", "torchflower_seeds"));
    }

    /**
     * Enable or disable animal farming mode.
     */
    public void setFarmingMode(boolean enabled) {
        farmingMode = enabled;
        LOGGER.info("Animal farming mode: {}", enabled);
    }

    /**
     * Main tick method for animal management.
     */
    public void tick(ServerPlayer player) {
        if (!farmingMode || player == null) return;

        try {
            ServerLevel level = (ServerLevel) player.level();
            BlockPos playerPos = player.blockPosition();

            // Find nearby animals and manage them
            findAndManageAnimals(level, playerPos, player);

        } catch (Exception e) {
            LOGGER.debug("AnimalManager tick error: {}", e.getMessage());
        }
    }

    /**
     * Find and manage nearby animals.
     */
    private void findAndManageAnimals(ServerLevel level, BlockPos center, ServerPlayer player) {
        int radius = 32;

        for (net.minecraft.world.entity.Entity entity : level.getEntities(null,
            new net.minecraft.world.phys.AABB(
                center.getX() - radius, center.getY() - 8, center.getZ() - radius,
                center.getX() + radius, center.getY() + 8, center.getZ() + radius
            ))) {

            if (entity instanceof Animal animal) {
                manageAnimal(animal, player);
            }
        }
    }

    /**
     * Manage a specific animal (feed, breed, etc.).
     */
    private void manageAnimal(Animal animal, ServerPlayer player) {
        String animalType = animal.getType().getDescriptionId().replace("entity.minecraft.", "");
        AnimalBreedingInfo info = breedingKnowledge.get(animalType);

        if (info == null) {
            LOGGER.debug("Unknown animal type: {}", animalType);
            return;
        }

        // Check if animal is ready to breed
        if (animal.getAge() >= 0 && info.canBreedNow()) {
            tryBreedAnimal(animal, info, player);
        }

        // Feed baby animals to speed up growth
        if (animal.getAge() < 0) {
            feedBabyAnimal(animal, info, player);
        }
    }

    /**
     * Attempt to breed an animal.
     */
    private void tryBreedAnimal(Animal animal, AnimalBreedingInfo info, ServerPlayer player) {
        // Find a mate nearby
        List<Animal> nearbyMates = findNearbyMates(animal, info.animalType, 8);

        if (nearbyMates.size() >= 1) { // Need at least one mate
            // Check if player has the required food
            if (hasBreedingFood(player, info.foodItem)) {
                // Feed the animals
                feedAnimal(animal, info, player);
                for (Animal mate : nearbyMates) {
                    if (mate != animal) {
                        feedAnimal(mate, info, player);
                        break; // Only breed with one pair at a time
                    }
                }

                info.lastBred = System.currentTimeMillis();
                LOGGER.info("Attempted to breed {} with {}", info.animalType, info.foodItem);
            }
        }
    }

    /**
     * Feed a baby animal to speed up growth.
     */
    private void feedBabyAnimal(Animal animal, AnimalBreedingInfo info, ServerPlayer player) {
        if (hasBreedingFood(player, info.foodItem)) {
            feedAnimal(animal, info, player);
        }
    }

    /**
     * Find nearby animals of the same type that can mate.
     */
    private List<Animal> findNearbyMates(Animal animal, String animalType, int radius) {
        List<Animal> mates = new ArrayList<>();
        BlockPos center = animal.blockPosition();

        for (net.minecraft.world.entity.Entity entity : animal.level().getEntities(null,
            new net.minecraft.world.phys.AABB(
                center.getX() - radius, center.getY() - 4, center.getZ() - radius,
                center.getX() + radius, center.getY() + 4, center.getZ() + radius
            ))) {

            if (entity instanceof Animal otherAnimal &&
                otherAnimal != animal &&
                otherAnimal.getType().getDescriptionId().contains(animalType) &&
                otherAnimal.getAge() >= 0 &&
                otherAnimal.canFallInLove()) {
                mates.add(otherAnimal);
            }
        }

        return mates;
    }

    /**
     * Check if player has the required breeding food.
     */
    private boolean hasBreedingFood(ServerPlayer player, String foodItem) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getItem().getDescriptionId().replace("item.minecraft.", "");
                if (itemName.equals(foodItem)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Feed an animal with the required food.
     */
    private void feedAnimal(Animal animal, AnimalBreedingInfo info, ServerPlayer player) {
        var inventory = player.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getItem().getDescriptionId().replace("item.minecraft.", "");
                if (itemName.equals(info.foodItem)) {
                    // Use the item on the animal
                    try {
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
                        com.tyler.forgeai.util.PlayerActionUtils.lookAtEntity(player, animal);
                        // Simulate right-click interaction
                        stack.shrink(1);
                        LOGGER.debug("Fed {} to {}", info.foodItem, info.animalType);
                    } catch (Exception e) {
                        LOGGER.debug("Failed to feed animal: {}", e.getMessage());
                    }
                    break;
                }
            }
        }
    }

    /**
     * Learn new breeding information from player instructions.
     */
    public void learnBreedingInfo(String animalType, String foodItem) {
        AnimalBreedingInfo info = breedingKnowledge.computeIfAbsent(animalType,
            k -> new AnimalBreedingInfo(animalType, foodItem));
        info.foodItem = foodItem;
        LOGGER.info("Learned breeding info: {} breeds with {}", animalType, foodItem);
    }

    /**
     * Get breeding information for an animal type.
     */
    public AnimalBreedingInfo getBreedingInfo(String animalType) {
        return breedingKnowledge.get(animalType);
    }

    /**
     * Get all known animal types.
     */
    public Set<String> getKnownAnimalTypes() {
        return new HashSet<>(breedingKnowledge.keySet());
    }
}
