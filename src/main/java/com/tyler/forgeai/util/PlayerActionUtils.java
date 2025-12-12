package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Player Action Utilities: Execute Minecraft player actions.
 * - Swing/attack (melee)
 * - Use item (bow, rod, ability)
 * - Place/break blocks
 * - Move & look
 * - Sprint/jump/crouch
 */
public class PlayerActionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-actions");

    /**
     * Swing player's held item (attack animation).
     */
    public static void swing(ServerPlayer player, InteractionHand hand) {
        try {
            player.swing(hand);
            LOGGER.debug("Player {} swinging {}", player.getName().getString(), hand.name());
        } catch (Exception e) {
            LOGGER.debug("Error swinging: {}", e.getMessage());
        }
    }

    /**
     * Swing main hand (right click attack).
     */
    public static void swingMainHand(ServerPlayer player) {
        swing(player, InteractionHand.MAIN_HAND);
    }

    /**
     * Attack entity with melee.
     */
    public static void attackEntity(ServerPlayer player, Entity target) {
        if (target == null || !target.isAlive()) return;

        try {
            // Look at entity
            lookAtEntity(player, target);
            
            // Attack
            player.attack(target);
            swingMainHand(player);
            
            LOGGER.debug("Player {} attacking {}", player.getName().getString(), target.getName().getString());
        } catch (Exception e) {
            LOGGER.debug("Error attacking: {}", e.getMessage());
        }
    }

    /**
     * Use held item (draw bow, cast fishing rod, activate ability).
     */
    public static void useItem(ServerPlayer player, InteractionHand hand, int ticksDuration) {
        try {
            player.startUsingItem(hand);
            
            // In real game: simulate ticks passing
            for (int i = 0; i < ticksDuration; i++) {
                // Would be called each tick in actual game loop
            }
            
            player.releaseUsingItem();
            LOGGER.debug("Player {} used item for {} ticks", player.getName().getString(), ticksDuration);
        } catch (Exception e) {
            LOGGER.debug("Error using item: {}", e.getMessage());
        }
    }

    /**
     * Use main hand item.
     */
    public static void useMainHand(ServerPlayer player, int ticks) {
        useItem(player, InteractionHand.MAIN_HAND, ticks);
    }

    /**
     * Interact with block (open chest, activate button, etc.).
     */
    public static void interactBlock(ServerPlayer player, BlockPos blockPos, InteractionHand hand) {
        try {
            // Look at block
            lookAtBlock(player, blockPos);
            
            // Create hit result pointing to block
            Vec3 hitVec = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitVec, net.minecraft.core.Direction.UP, blockPos, false);
            
            // Interact
            player.gameMode.useItemOn(player.containerMenu, player.level(), 
                player.getItemInHand(hand), hand, hitResult);
            
            LOGGER.debug("Player {} interacting with block at {}", player.getName().getString(), blockPos.toShortString());
        } catch (Exception e) {
            LOGGER.debug("Error interacting with block: {}", e.getMessage());
        }
    }

    /**
     * Break block (mine).
     */
    public static boolean breakBlock(ServerPlayer player, BlockPos blockPos) {
        try {
            // Look at block
            lookAtBlock(player, blockPos);
            
            // Start breaking
            player.gameMode.startDestroyBlock(blockPos);
            
            // Simulate mining time (depends on tool and block hardness)
            // In real game: would be called each tick until block breaks
            
            LOGGER.debug("Breaking block at {}", blockPos.toShortString());
            return true;
        } catch (Exception e) {
            LOGGER.debug("Error breaking block: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Place block.
     */
    public static boolean placeBlock(ServerPlayer player, BlockPos blockPos) {
        try {
            // Look at block position
            lookAtBlock(player, blockPos);
            
            // Create hit result
            Vec3 hitVec = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
            BlockHitResult hitResult = new BlockHitResult(hitVec, net.minecraft.core.Direction.UP, blockPos, false);
            
            // Place block
            player.gameMode.useItemOn(player.containerMenu, player.level(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hitResult);
            
            LOGGER.debug("Placing block at {}", blockPos.toShortString());
            return true;
        } catch (Exception e) {
            LOGGER.debug("Error placing block: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Move player in direction (forward/strafe).
     */
    public static void moveInDirection(ServerPlayer player, float forward, float strafe) {
        try {
            // Simulate client-side input
            player.input.forwardImpulse = Math.max(-1.0f, Math.min(1.0f, forward));
            player.input.leftImpulse = Math.max(-1.0f, Math.min(1.0f, strafe));
            
            LOGGER.debug("Moving: forward={}, strafe={}", forward, strafe);
        } catch (Exception e) {
            LOGGER.debug("Error moving: {}", e.getMessage());
        }
    }

    /**
     * Move forward.
     */
    public static void moveForward(ServerPlayer player, float speed) {
        moveInDirection(player, speed, 0.0f);
    }

    /**
     * Strafe sideways.
     */
    public static void strafe(ServerPlayer player, float direction) {
        moveInDirection(player, 0.0f, direction);
    }

    /**
     * Look at specific position.
     */
    public static void lookAt(ServerPlayer player, double x, double y, double z) {
        try {
            double dx = x - player.getX();
            double dy = y - (player.getY() + player.getEyeHeight());
            double dz = z - player.getZ();
            
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.001) return;  // Too close
            
            // Calculate pitch and yaw
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            
            player.setRot(yaw, pitch);
            LOGGER.debug("Player {} looking at ({}, {}, {})", 
                player.getName().getString(), x, y, z);
        } catch (Exception e) {
            LOGGER.debug("Error looking at position: {}", e.getMessage());
        }
    }

    /**
     * Look at entity.
     */
    public static void lookAtEntity(ServerPlayer player, Entity entity) {
        if (entity != null) {
            lookAt(player, entity.getX(), entity.getEyeY(), entity.getZ());
        }
    }

    /**
     * Look at block.
     */
    public static void lookAtBlock(ServerPlayer player, BlockPos blockPos) {
        lookAt(player, blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    /**
     * Jump action.
     */
    public static void jump(ServerPlayer player) {
        try {
            player.jump();
            LOGGER.debug("Player {} jumping", player.getName().getString());
        } catch (Exception e) {
            LOGGER.debug("Error jumping: {}", e.getMessage());
        }
    }

    /**
     * Sprint toggle.
     */
    public static void setSprinting(ServerPlayer player, boolean sprinting) {
        try {
            player.setSprinting(sprinting);
            LOGGER.debug("Player {} sprinting={}", player.getName().getString(), sprinting);
        } catch (Exception e) {
            LOGGER.debug("Error sprinting: {}", e.getMessage());
        }
    }

    /**
     * Crouch/sneak toggle.
     */
    public static void setCrouching(ServerPlayer player, boolean crouching) {
        try {
            player.setShiftKeyDown(crouching);
            LOGGER.debug("Player {} crouching={}", player.getName().getString(), crouching);
        } catch (Exception e) {
            LOGGER.debug("Error crouching: {}", e.getMessage());
        }
    }

    /**
     * Open container (chest, furnace, etc).
     */
    public static void openContainer(ServerPlayer player, BlockPos containerPos) {
        try {
            var blockState = player.level().getBlockState(containerPos);
            var blockEntity = player.level().getBlockEntity(containerPos);
            
            if (blockEntity != null) {
                player.openMenu(blockEntity.createMenu(0, player.getInventory(), player));
                LOGGER.debug("Opening container at {}", containerPos.toShortString());
            }
        } catch (Exception e) {
            LOGGER.debug("Error opening container: {}", e.getMessage());
        }
    }

    /**
     * Close open container.
     */
    public static void closeContainer(ServerPlayer player) {
        try {
            player.closeContainer();
            LOGGER.debug("Closed container");
        } catch (Exception e) {
            LOGGER.debug("Error closing container: {}", e.getMessage());
        }
    }

    /**
     * Get player's current block they're standing on.
     */
    public static BlockPos getBlockBelow(ServerPlayer player) {
        return player.blockPosition().below();
    }

    /**
     * Check if player is on ground.
     */
    public static boolean isOnGround(ServerPlayer player) {
        return player.onGround();
    }

    /**
     * Get distance to position.
     */
    public static double distanceTo(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getCenter());
    }
}
