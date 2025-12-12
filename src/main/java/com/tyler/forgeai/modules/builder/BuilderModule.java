package com.tyler.forgeai.modules.builder;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuilderModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-builder");

    private boolean active = false;

    public void init() {
        LOGGER.info("Builder module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Builder module active: " + enabled);
    }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Builder tick running for player: " + s.player.getName().getString());

        if (s.isBuildingPhase()) {
            placeBlocks(s);
        } else {
            LOGGER.debug("Not in building phase — BuilderModule idle.");
        }
    }

    private void placeBlocks(Signals s) {
        try {
            var player = s.player;
            if (player == null) return;

            var main = player.getMainHandItem();
            if (main == null || main.isEmpty() || !(main.getItem() instanceof net.minecraft.world.item.BlockItem)) {
                LOGGER.debug("No block in main hand to place");
                return;
            }

            // Place block directly in front of player
            net.minecraft.core.BlockPos target = player.blockPosition().offset(player.getLookAngle().x > 0 ? 1 : -1, 0, 0);
            com.tyler.forgeai.util.PlayerActionUtils.placeBlock(player, target);
            LOGGER.info("Placed block at {}", target.toShortString());
        } catch (Exception e) {
            LOGGER.debug("Builder placeBlocks error: {}", e.getMessage());
        }
        LOGGER.info("Placing blocks in builder mode.");
        // Example: read blueprint, place blocks sequentially, ensure inventory has required materials
    }
}
