package com.tyler.forgeai.core;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-context");

    public void init() {
        LOGGER.info("ContextScanner initialized.");
    }

    public Signals sample(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().stream().findFirst().orElse(null);
        return Signals.from(player);
    }

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
            boolean building = player.getMainHandItem().isBlock();
            boolean stasis = !inCombat && !isFlying && !needsRes && !building;

            return new Signals(player, inCombat, isFlying, maceEquipped, crystalOpp, needsRes, building, stasis);
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
