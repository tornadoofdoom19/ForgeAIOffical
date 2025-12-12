package com.tyler.forgeai.modules.pvp;

import com.tyler.forgeai.core.ContextScanner.Signals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrystalModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-crystal");

    private boolean active = false;

    public void init() {
        LOGGER.info("Crystal PvP module initialized.");
    }

    public void setActive(boolean enabled) {
        active = enabled;
        LOGGER.info("Crystal PvP module active: " + enabled);
    }

    public void tick(Signals s) {
        if (!active || s.player == null) return;

        LOGGER.debug("Crystal PvP tick running for player: " + s.player.getName().getString());

        if (s.crystalOpportunity()) {
            placeCrystal(s);
            detonateCrystal(s);
        } else {
            LOGGER.debug("No crystal opportunity detected.");
        }
    }

    private void placeCrystal(Signals s) {
        LOGGER.info("Placing end crystal for PvP attack.");
        try {
            // Find obsidian/bedrock spot near opponent
            var opp = s.player.level().getNearestPlayer(s.player, 64);
            if (opp == null) return;
            net.minecraft.core.BlockPos target = null;
            for (net.minecraft.core.BlockPos pos :
                net.minecraft.core.BlockPos.betweenClosed(
                    opp.blockPosition().offset(-4, -2, -4),
                    opp.blockPosition().offset(4, 2, 4))) {
                var state = s.player.level().getBlockState(pos);
                String name = state.getBlock().getName().getString().toLowerCase();
                if (name.contains("obsidian") || name.contains("bedrock")) { target = pos; break; }
            }

            if (target == null) return;

            com.tyler.forgeai.util.PlayerActionUtils.lookAtBlock(s.player, target);
            com.tyler.forgeai.util.InventoryUtils.moveItemToHotbar(s.player, "end_crystal");
            com.tyler.forgeai.util.PlayerActionUtils.useMainHand(s.player, 2);
        } catch (Exception e) {
            LOGGER.debug("Error placing crystal: {}", e.getMessage());
        }
    }

    private void detonateCrystal(Signals s) {
        LOGGER.info("Detonating end crystal for maximum damage.");
        try {
            // Find nearest end crystal entity and attack it
            var crystals = s.player.level().getEntitiesOfClass(net.minecraft.world.entity.decoration.EndCrystal.class,
                s.player.getBoundingBox().inflate(8));
            if (crystals == null || crystals.isEmpty()) return;
            var crystal = crystals.get(0);
            com.tyler.forgeai.util.PlayerActionUtils.lookAtEntity(s.player, crystal);
            com.tyler.forgeai.util.PlayerActionUtils.attackEntity(s.player, crystal);
        } catch (Exception e) {
            LOGGER.debug("Error detonating crystal: {}", e.getMessage());
        }
    }
}
