package com.tyler.forgeai.modules.gatherer;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GathererModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-gatherer");

    private boolean active = false;

    public void init() {
        LOGGER.info("Gatherer module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Gatherer module active: " + enabled);
    }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Gatherer tick running for player: " + s.player.getName().getString());

        if (s.needsResources()) {
            collectResources(s);
        } else {
            LOGGER.debug("No resource need detected — GathererModule idle.");
        }
    }

    private void collectResources(Signals s) {
        // TODO: implement resource gathering logic
        LOGGER.info("Collecting resources in gatherer mode.");
        // Example: mine ores, chop trees, harvest crops, ensure inventory space
    }
}
