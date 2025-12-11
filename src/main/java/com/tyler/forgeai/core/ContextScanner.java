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

        private Signals(ServerPlayer player,
                        boolean inCombat,
                        boolean isFlyingWithElytra,
                        boolean hasMaceEquipped,
                        boolean crystalOpportunity,
                        boolean needsResources,
                        boolean isBuildingPhase,
                        boolean shouldEnterStasis) {
            this.player = player;
            this.inCombat = inCombat;
            this.isFlyingWithElytra = isFlyingWithElytra;
            this.hasMaceEquipped = hasMaceEquipped;
            this.crystalOpportunity = crystalOpportunity;
            this.needsResources = needsResources;
            this.isBuildingPhase = isBuildingPhase;
            this.shouldEnterStasis = shouldEnterStasis;
        }

        public static Signals from(ServerPlayer player) {
            if (player == null) {
                return new Signals(null, false, false, false, false, false, false, true);
            }

            boolean inCombat = player.getLastHurtByMob() != null;
            boolean isFlying = player.isFallFlying();
            boolean maceEquipped = player.getMainHandItem().getItem().toString().toLowerCase().contains("mace");
            boolean crystalOpp = false; // TODO: detect obsidian + end crystal placement
            boolean needsRes = player.getInventory().isEmpty();
            // Check if item is a block type
            boolean building = !player.getMainHandItem().isEmpty() && player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem;
            boolean stasis = !inCombat && !isFlying && !needsRes && !building;

            return new Signals(player, inCombat, isFlying, maceEquipped, crystalOpp, needsRes, building, stasis);
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
            return new Signals(null, inCombat, isFlyingWithElytra, hasMaceEquipped, crystalOpportunity, needsResources, isBuildingPhase, shouldEnterStasis);
        }

        // Convenience getters
        public boolean inCombat() { return inCombat; }
        public boolean isFlyingWithElytra() { return isFlyingWithElytra; }
        public boolean hasMaceEquipped() { return hasMaceEquipped; }
        public boolean crystalOpportunity() { return crystalOpportunity; }
        public boolean needsResources() { return needsResources; }
        public boolean isBuildingPhase() { return isBuildingPhase; }
        public boolean shouldEnterStasis() { return shouldEnterStasis; }
    }
}
