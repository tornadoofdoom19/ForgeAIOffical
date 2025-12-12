package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CartModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-cart");

    private boolean active = false;

    public void init() {
        LOGGER.info("Cart PvP module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Cart PvP module active: " + enabled);
    }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Cart PvP tick running for player: " + s.player.getName().getString());

        if (s.inCombat()) {
            deployCartAttack(s);
        } else {
            LOGGER.debug("No combat detected — CartModule idle.");
        }
    }

    private void deployCartAttack(Signals s) {
        LOGGER.info("Deploying cart-based PvP attack.");
        try {
            // Find nearby rail to place minecart
            net.minecraft.core.BlockPos railPos = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    s.player.blockPosition().offset(-16, -2, -16),
                    s.player.blockPosition().offset(16, 2, 16))) {
                var state = s.player.level().getBlockState(pos);
                String name = state.getBlock().getName().getString().toLowerCase();
                if (name.contains("rail")) { railPos = pos; break; }
            }

            if (railPos == null) {
                LOGGER.info("No rail found for minecart deployment");
                return;
            }

            // Move to rail and attempt to place minecart item
            com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(s.player, railPos);
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(s.player, "minecart");
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(s.player, 2);

            // Push toward opponent
            var opp = s.player.level().getNearestPlayer(s.player, 64);
            if (opp != null) {
                com.tyler.forgeai.util.PlayerActionUtils.lookAtEntity(s.player, opp);
                com.tyler.forgeai.util.PlayerActionUtils.moveForward(s.player, 1.0f);
                try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                // Detonation: try to place TNT next to rail or use main hand again
                com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(s.player, "tnt");
                com.tyler.forgeai.util.PlayerActionUtils.useMainHand(s.player, 2);
            }
        } catch (Exception e) {
            LOGGER.debug("Error during cart attack: {}", e.getMessage());
        }
    }
}
