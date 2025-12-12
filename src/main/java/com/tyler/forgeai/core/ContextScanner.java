package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.item.BlockItem;import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-context");
    private Signals lastSignals = null;

    public void init() {
        LOGGER.info("ContextScanner initialized.");
    }

    public Signals sample(MinecraftServer server) {
        if (server == null) {
            return lastSignals;
        }

        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        lastSignals = Signals.from(player);
        return lastSignals;
    }

    public Signals getLastSignals() {
        return lastSignals;
    }

    /** Test-only: set the last sampled Signals so `sample(null)` will return it. */
    public void setLastSignals(Signals s) { this.lastSignals = s; }

    public static class Signals {
        public final ServerPlayer player;

        public final boolean inCombat;
        public final boolean isFlyingWithElytra;
        public final boolean hasMaceEquipped;
        public final boolean crystalOpportunity;
        public final boolean needsResources;
        public final boolean isBuildingPhase;
        public final boolean shouldEnterStasis;
        
        // Extended combat state
        public final float playerHealth;
        public final int foodLevel;
        public final float[] armorDurability;
        public final java.util.Set<String> activePotionEffects;
        public final boolean isLowHealth;
        public final boolean isArmorBroken;
        public final float incomingMeleeDamage;
        public final float incomingProjectileDamage;
        public final boolean opponentAirborne;
        public final boolean opponentHasShield;
        public final boolean hasRockets;
        public final int nearbyOpponents;
        public final boolean webTrapDetected;
        public final boolean falling;
        public final float fallHeight;
        public final boolean inLava;

        private Signals(ServerPlayer player,
                        boolean inCombat,
                        boolean isFlyingWithElytra,
                        boolean hasMaceEquipped,
                        boolean crystalOpportunity,
                        boolean needsResources,
                        boolean isBuildingPhase,
                        boolean shouldEnterStasis,
                        float playerHealth,
                        int foodLevel,
                        float[] armorDurability,
                        java.util.Set<String> activePotionEffects,
                        boolean isLowHealth,
                        boolean isArmorBroken,
                        float incomingMeleeDamage,
                        float incomingProjectileDamage,
                        boolean opponentAirborne,
                        boolean opponentHasShield,
                        boolean hasRockets,
                        int nearbyOpponents,
                        boolean webTrapDetected,
                        boolean falling,
                        float fallHeight,
                        boolean inLava) {
            this.player = player;
            this.inCombat = inCombat;
            this.isFlyingWithElytra = isFlyingWithElytra;
            this.hasMaceEquipped = hasMaceEquipped;
            this.crystalOpportunity = crystalOpportunity;
            this.needsResources = needsResources;
            this.isBuildingPhase = isBuildingPhase;
            this.shouldEnterStasis = shouldEnterStasis;
            this.playerHealth = playerHealth;
            this.foodLevel = foodLevel;
            this.armorDurability = armorDurability;
            this.activePotionEffects = activePotionEffects;
            this.isLowHealth = isLowHealth;
            this.isArmorBroken = isArmorBroken;
            this.incomingMeleeDamage = incomingMeleeDamage;
            this.incomingProjectileDamage = incomingProjectileDamage;
            this.opponentAirborne = opponentAirborne;
            this.opponentHasShield = opponentHasShield;
            this.hasRockets = hasRockets;
            this.nearbyOpponents = nearbyOpponents;
            this.webTrapDetected = webTrapDetected;
            this.falling = falling;
            this.fallHeight = fallHeight;
            this.inLava = inLava;
        }

        public static Signals from(ServerPlayer player) {
            if (player == null) {
                return new Signals(null, false, false, false, false, false, false, true,
                        20.0f, 20, new float[]{1.0f, 1.0f, 1.0f, 1.0f}, new java.util.HashSet<>(),
                        false, false, 0, 0, false, false, false, 0, false, false, 0, false);
            }

            boolean inCombat = player.getLastHurtByMob() != null;
            boolean isFlying = player.isFallFlying();
            boolean maceEquipped = player.getMainHandItem().getItem().toString().toLowerCase().contains("mace");
            boolean crystalOpp = false;
            // Check for obsidian or end crystal nearby
            var nearbyBlocks = player.level().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, player.getBoundingBox().inflate(16));
            for (var entity : (java.util.List<?>) nearbyBlocks) {
                var passenger = entity.getPassengers();
                for (var pass : passenger) {
                    if (pass.getType().toString().contains("end_crystal")) { crystalOpp = true; break; }
                }
            }
            boolean needsRes = player.getInventory().isEmpty();
            // Check if item is a block type
            boolean building = !player.getMainHandItem().isEmpty() && player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem;
            boolean stasis = !inCombat && !isFlying && !needsRes && !building;
            
            // Extended combat state
            float health = player.getHealth();
            int food = player.getFoodLevel();
            boolean lowHealth = health < 6.0f;
            // Check armor durability (all pieces)
            boolean armorBroken = false;
            try {
                for (var armor : player.getArmorSlots()) {
                    if (!armor.isEmpty() && armor.getDamageValue() > armor.getMaxDamage() * 0.8f) {
                        armorBroken = true; break;
                    }
                }
            } catch (Exception ignored) {}
            boolean hasRockets = false;
            int nearbyOpponents = 0;
            try {
                String main = player.getMainHandItem().getItem().toString().toLowerCase();
                String off = player.getOffhandItem().getItem().toString().toLowerCase();
                hasRockets = main.contains("rocket") || off.contains("rocket") || main.contains("firework") || off.contains("firework");
            } catch (Exception ignored) {}
            try {
                nearbyOpponents = (int) player.getLevel().players().stream()
                    .filter(p -> !p.getUUID().equals(player.getUUID()) && p.distanceTo(player) < 12.0)
                    .count();
                } catch (Exception ignored) { nearbyOpponents = 0; }

                return new Signals(player, inCombat, isFlying, maceEquipped, crystalOpp, needsRes, building, stasis,
                    health, food, new float[]{1.0f, 1.0f, 1.0f, 1.0f}, new java.util.HashSet<>(),
                    lowHealth, armorBroken, 0, 0, false, false, hasRockets, nearbyOpponents, false, false, 0, false);
        }

        /**
         * Create a synthetic Signals object for test harness usage where a real
         * `ServerPlayer` instance is not available. This is intentionally simple
         * and should only be used by testing utilities.
         */
        public static Signals synthetic(boolean inCombat,
                                        boolean isFlyingWithElytra,
                                        boolean hasMaceEquipped,
                                        boolean crystalOpportunity,
                                        boolean needsResources,
                                        boolean isBuildingPhase,
                                        boolean shouldEnterStasis) {
                return new Signals(null, inCombat, isFlyingWithElytra, hasMaceEquipped, crystalOpportunity, needsResources, isBuildingPhase, shouldEnterStasis,
                    20.0f, 20, new float[]{1.0f, 1.0f, 1.0f, 1.0f}, new java.util.HashSet<>(),
                    false, false, 0, 0, false, false, false, 0, false, false, 0, false);
        }
        
        /**
         * Create a comprehensive synthetic Signals object with full combat state for advanced testing.
         */
        public static Signals syntheticFull(boolean inCombat,
                        boolean isFlyingWithElytra,
                        boolean hasMaceEquipped,
                        boolean crystalOpportunity,
                        boolean needsResources,
                        boolean isBuildingPhase,
                        boolean shouldEnterStasis,
                        float playerHealth,
                        int foodLevel,
                        float[] armorDurability,
                        java.util.Set<String> activePotionEffects,
                        boolean isLowHealth,
                        boolean isArmorBroken,
                        float incomingMeleeDamage,
                        float incomingProjectileDamage,
                        boolean opponentAirborne,
                        boolean opponentHasShield,
                        boolean hasRockets,
                        int nearbyOpponents,
                        boolean webTrapDetected,
                        boolean falling,
                        float fallHeight,
                        boolean inLava) {
            return new Signals(null, inCombat, isFlyingWithElytra, hasMaceEquipped, crystalOpportunity, needsResources, isBuildingPhase, shouldEnterStasis,
                playerHealth, foodLevel, armorDurability, activePotionEffects,
                isLowHealth, isArmorBroken, incomingMeleeDamage, incomingProjectileDamage,
                opponentAirborne, opponentHasShield, hasRockets, nearbyOpponents, webTrapDetected, falling, fallHeight, inLava);
        }

        // Convenience getters
        public boolean inCombat() { return inCombat; }
        public boolean isFlyingWithElytra() { return isFlyingWithElytra; }
        public boolean hasMaceEquipped() { return hasMaceEquipped; }
        public boolean crystalOpportunity() { return crystalOpportunity; }
        public boolean needsResources() { return needsResources; }
        public boolean isBuildingPhase() { return isBuildingPhase; }
        public boolean shouldEnterStasis() { return shouldEnterStasis; }
        public boolean hasRockets() { return hasRockets; }
        public int nearbyOpponents() { return nearbyOpponents; }
    }
}
