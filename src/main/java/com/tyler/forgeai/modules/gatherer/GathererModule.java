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
        // Best-effort gathering logic: scan for basic resources (wood, stone, ores)
        try {
            var player = s.player;
            if (player == null) return;

            String[] resources = new String[]{"log", "iron_ore", "coal_ore", "stone", "dirt", "oak_log", "copper_ore"};
            boolean gatheredAny = false;
            for (String resource : resources) {
                // Quick scan for blocks containing the keyword
                for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-16, -4, -16), player.blockPosition().offset(16, 8, 16))) {
                    var state = player.level().getBlockState(pos);
                    String blk = state.getBlock().getName().getString().toLowerCase();
                    if (blk.contains(resource)) {
                        com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(player, pos);
                        com.tyler.forgeai.util.PlayerActionUtils.breakBlock(player, pos);
                        gatheredAny = true;
                        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        break;
                    }
                }
                if (gatheredAny) break;
            }

            if (!gatheredAny) {
                LOGGER.debug("No immediate resources found to gather");
            } else {
                LOGGER.info("Gathered a resource on request");
            }
        } catch (Exception e) {
            LOGGER.debug("collectResources error: {}", e.getMessage());
        }
        LOGGER.info("Collecting resources in gatherer mode.");
        // Example: mine ores, chop trees, harvest crops, ensure inventory space
    }
}
