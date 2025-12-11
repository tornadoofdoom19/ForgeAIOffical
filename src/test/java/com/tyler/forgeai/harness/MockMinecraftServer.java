package com.tyler.forgeai.harness;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal mock implementation of MinecraftServer for testing.
 * Provides just enough interface to support ContextScanner.sample() and DecisionEngine.tick().
 */
public class MockMinecraftServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-harness");

    // Simulate a player for testing
    private MockServerPlayer mockPlayer;
    private final List<MockServerPlayer> players = new ArrayList<>();

    public MockMinecraftServer() {
        this.mockPlayer = new MockServerPlayer("TestBot", 100.0); // 100 health (5 hearts)
        this.players.add(mockPlayer);
    }

    /**
     * Returns mock player list for ContextScanner to sample.
     */
    public List<MockServerPlayer> getPlayers() {
        return new ArrayList<>(players);
    }

    /**
     * Get the primary test player.
     */
    public MockServerPlayer getPrimaryPlayer() {
        return mockPlayer;
    }

    /**
     * Reset player state for next test.
     */
    public void resetPlayerState() {
        mockPlayer.resetCombatState();
    }

    /**
     * Simulate damage to player.
     */
    public void damagePlayer(double damage) {
        mockPlayer.setHealth(mockPlayer.getHealth() - damage);
    }

    /**
     * Get current player health.
     */
    public double getPlayerHealth() {
        return mockPlayer.getHealth();
    }

    /**
     * Mock ServerPlayer for testing.
     */
    public static class MockServerPlayer {
        private final String name;
        private double health;
        private Object lastHurtBy = null;
        private boolean fallFlying = false;
        private Object mainHandItem = null;
        private Object inventory = null;

        public MockServerPlayer(String name, double health) {
            this.name = name;
            this.health = health;
        }

        public String getName() {
            return name;
        }

        public double getHealth() {
            return health;
        }

        public void setHealth(double health) {
            this.health = Math.max(0, health);
        }

        public Object getLastHurtBy() {
            return lastHurtBy;
        }

        public void setLastHurtBy(Object entity) {
            this.lastHurtBy = entity;
        }

        public void clearLastHurtBy() {
            this.lastHurtBy = null;
        }

        public boolean isFallFlying() {
            return fallFlying;
        }

        public void setFallFlying(boolean flying) {
            this.fallFlying = flying;
        }

        public Object getMainHandItem() {
            return mainHandItem;
        }

        public void setMainHandItem(Object item) {
            this.mainHandItem = item;
        }

        public Object getInventory() {
            return inventory;
        }

        public void setInventory(Object inv) {
            this.inventory = inv;
        }

        public void resetCombatState() {
            lastHurtBy = null;
            fallFlying = false;
            mainHandItem = null;
            health = 100.0;
        }

        @Override
        public String toString() {
            return "MockServerPlayer{" +
                    "name='" + name + '\'' +
                    ", health=" + health +
                    ", inCombat=" + (lastHurtBy != null) +
                    ", flying=" + fallFlying +
                    '}';
        }
    }
}
