package com.tyler.forgeai.core;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event Hook Registration: Wire CombatEventHandler to actual Minecraft events.
 * Enables real-time RL feedback from gameplay.
 */
public class EventHookRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-events");

    public static void registerCombatEventHooks() {
        LOGGER.info("Registering combat event hooks...");

        // Hook: Entity damage event (enables damage reporting)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                // Report damage to combat event handler
                CombatEventHandler.reportPlayerDamage(player, source, amount);
            }
            return true;  // Allow damage
        });

        // Hook: Entity death event (enables kill reporting)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer deadPlayer) {
                // Report player death
                CombatEventHandler.reportPlayerKilled(deadPlayer, damageSource);
            } else {
                // Report mob/entity kill to nearby players
                for (LivingEntity nearby : entity.level().getEntitiesOfClass(
                    ServerPlayer.class, 
                    entity.getBoundingBox().inflate(32))) {
                    if (nearby instanceof ServerPlayer player) {
                        CombatEventHandler.reportEnemyKilled(player, entity);
                    }
                }
            }
        });

        LOGGER.info("Combat event hooks registered successfully");
    }

    /**
     * Extended: Register navigation event hooks.
     */
    public static void registerNavigationEventHooks() {
        LOGGER.info("Registering navigation event hooks...");

        // Hook: Teleport event (enables portal/ender pearl tracking)
        // This would be registered via EntityTeleportEvent in Fabric/Forge
        // Stub: actual implementation depends on mod loader event API

        LOGGER.info("Navigation event hooks registered (stubs only)");
    }

    /**
     * Extended: Register resource gathering event hooks.
     */
    public static void registerGatheringEventHooks() {
        LOGGER.info("Registering gathering event hooks...");

        // Hook: Block break event (enables mining task feedback)
        // This would be registered via BlockBreakEvent
        // Stub: actual implementation depends on mod loader event API

        LOGGER.info("Gathering event hooks registered (stubs only)");
    }
}
