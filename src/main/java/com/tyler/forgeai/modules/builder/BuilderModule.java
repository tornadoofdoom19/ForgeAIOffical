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
        // TODO: implement block placement logic
        LOGGER.info("Placing blocks in builder mode.");
        // Example: read blueprint, place blocks sequentially, ensure inventory has required materials
    }
}
