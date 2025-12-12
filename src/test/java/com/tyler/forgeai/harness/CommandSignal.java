package com.tyler.forgeai.harness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Command signal system for PvP testing.
 * - Direct commands: explicit module activation (e.g., "use sword", "switch to shield")
 * - Indirect signals: implicit combat cues (low health, blocked crystal, armor break, potion available)
 */
public class CommandSignal {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-command");

    public enum CommandType {
        DIRECT_SWORD("Direct: Use Sword", true),
        DIRECT_MACE("Direct: Use Mace", true),
        DIRECT_SHIELD("Direct: Switch to Shield", true),
        DIRECT_TOTEM("Direct: Prepare Totem", true),
        DIRECT_CRYSTAL("Direct: Use Crystal", true),
        DIRECT_BOW("Direct: Use Bow", true),
        DIRECT_PEARLS("Direct: Use Ender Pearls", true),
        DIRECT_ELYTRA("Direct: Engage Elytra", true),
        DIRECT_DISENGAGE("Direct: Disengage/Run", true),
        INDIRECT_LOW_HEALTH("Signal: Low Health", false),
        INDIRECT_BLOCKED_CRYSTAL("Signal: Crystal Blocked", false),
        INDIRECT_ARMOR_BREAKING("Signal: Armor Breaking", false),
        INDIRECT_POTION_AVAILABLE("Signal: Potion Available", false),
        INDIRECT_OPPONENT_AIRBORNE("Signal: Opponent Airborne", false),
        INDIRECT_WEB_STUCK("Signal: Stuck in Web", false),
        INDIRECT_FALLING("Signal: Falling", false),
        INDIRECT_LAVA_DAMAGE("Signal: Lava Damage", false),
        INDIRECT_SHIELD_EFFECTIVE("Signal: Shield Blocking", false),
        INDIRECT_TOTEM_NEEDED("Signal: Lethal Damage Imminent", false),
        INDIRECT_RESTOCK_NEEDED("Signal: Resources Critical", false);

        private final String displayName;
        private final boolean isDirect;

        CommandType(String displayName, boolean isDirect) {
            this.displayName = displayName;
            this.isDirect = isDirect;
        }

        public String getDisplayName() { return displayName; }
        public boolean isDirect() { return isDirect; }
        public boolean isIndirect() { return !isDirect; }
    }

    private final CommandType commandType;
    private final String source; // Name of module/system that issued signal
    private final long timestamp;
    private final Map<String, Object> metadata = new HashMap<>();
    private float priority = 0.5f; // 0.0 = low, 1.0 = critical
    private String rationale = "";

    public CommandSignal(CommandType commandType, String source) {
        this.commandType = commandType;
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    // ---- Fluent Builder ----

    public CommandSignal withPriority(float priority) {
        this.priority = Math.max(0, Math.min(1, priority));
        return this;
    }

    public CommandSignal withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public CommandSignal withRationale(String rationale) {
        this.rationale = rationale;
        return this;
    }

    // ---- Getters ----

    public CommandType getCommandType() { return commandType; }
    public String getSource() { return source; }
    public long getTimestamp() { return timestamp; }
    public float getPriority() { return priority; }
    public String getRationale() { return rationale; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    public boolean isDirect() { return commandType.isDirect(); }
    public boolean isIndirect() { return commandType.isIndirect(); }

    @Override
    public String toString() {
        return String.format("[%s] %s (priority=%.2f) from %s",
                isDirect() ? "DIRECT" : "SIGNAL", commandType.getDisplayName(), priority, source);
    }

    // ---- Static Factory Methods ----

    /**
     * Generate random command signals based on inventory and scenario state.
     */
    public static List<CommandSignal> generateSignalsFromState(
            RandomizedPvPInventory inventory,
            RandomizedPvPScenarioGenerator.PvPScenarioState scenario) {

        List<CommandSignal> signals = new ArrayList<>();

        // Analyze health
        if (inventory.isLowHealth()) {
            signals.add(new CommandSignal(CommandType.INDIRECT_LOW_HEALTH, "ContextScanner")
                    .withPriority(0.8f)
                    .withRationale("Player health < 3 hearts, consider shield or elytra escape")
                    .withMetadata("currentHealth", inventory.getPlayerHealth()));
        }

        // Analyze armor
        if (inventory.isArmorBroken()) {
            signals.add(new CommandSignal(CommandType.INDIRECT_ARMOR_BREAKING, "ContextScanner")
                    .withPriority(0.7f)
                    .withRationale("Armor durability critical, disengage to restock")
                    .withMetadata("armorStatus", "broken"));
        }

        // Analyze potion availability
        if (!inventory.getActivePotions().isEmpty()) {
            signals.add(new CommandSignal(CommandType.INDIRECT_POTION_AVAILABLE, "ContextScanner")
                    .withPriority(0.5f)
                    .withRationale("Active potions available, use advantageously")
                    .withMetadata("potions", inventory.getActivePotions().size()));
        }

        // Scenario-based signals
        if (scenario.opponentAirborne) {
            signals.add(new CommandSignal(CommandType.INDIRECT_OPPONENT_AIRBORNE, "ContextScanner")
                    .withPriority(0.7f)
                    .withRationale("Opponent airborne, use mace or pearls for aerial pressure")
                    .withMetadata("opponentHeight", "airborne"));
        }

        if (scenario.webTrapDetected) {
            signals.add(new CommandSignal(CommandType.INDIRECT_WEB_STUCK, "ContextScanner")
                    .withPriority(0.8f)
                    .withRationale("Stuck in web, break immediately or use escape item")
                    .withMetadata("restrictedMovement", true));
        }

        if (scenario.falling) {
            signals.add(new CommandSignal(CommandType.INDIRECT_FALLING, "ContextScanner")
                    .withPriority(0.9f)
                    .withRationale("Falling " + scenario.fallHeight + " blocks, use water bucket or elytra")
                    .withMetadata("fallHeight", scenario.fallHeight));
        }

        if (scenario.inLava) {
            signals.add(new CommandSignal(CommandType.INDIRECT_LAVA_DAMAGE, "ContextScanner")
                    .withPriority(0.9f)
                    .withRationale("In lava, use water bucket or escape immediately")
                    .withMetadata("damagePerTick", scenario.incomingEnvironmentalDamage));
        }

        if (scenario.opponentShieldActive) {
            signals.add(new CommandSignal(CommandType.INDIRECT_SHIELD_EFFECTIVE, "ContextScanner")
                    .withPriority(0.6f)
                    .withRationale("Opponent shielding, pressure with sword or use pearls to flank")
                    .withMetadata("shieldDurability", "high"));
        }

        if (scenario.incomingDamageRisk > 0.7f) {
            signals.add(new CommandSignal(CommandType.INDIRECT_TOTEM_NEEDED, "ContextScanner")
                    .withPriority(0.95f)
                    .withRationale("Lethal damage imminent, prepare totem or disengage immediately")
                    .withMetadata("riskLevel", scenario.incomingDamageRisk));
        }

        if (!inventory.hasItem(RandomizedPvPInventory.PvPItem.TOTEM) &&
                !inventory.hasItem(RandomizedPvPInventory.PvPItem.ENDER_PEARL)) {
            signals.add(new CommandSignal(CommandType.INDIRECT_RESTOCK_NEEDED, "ContextScanner")
                    .withPriority(0.85f)
                    .withRationale("No totems or pearls, critical resources depleted, disengage to restock")
                    .withMetadata("resourceState", "critical"));
        }

        return signals;
    }

    /**
     * Generate random direct commands (explicit player requests).
     */
    public static CommandSignal generateRandomDirectCommand(RandomizedPvPInventory inventory) {
        CommandType[] directCommands = {
                CommandType.DIRECT_SWORD,
                CommandType.DIRECT_MACE,
                CommandType.DIRECT_SHIELD,
                CommandType.DIRECT_TOTEM,
                CommandType.DIRECT_CRYSTAL,
                CommandType.DIRECT_BOW,
                CommandType.DIRECT_PEARLS,
                CommandType.DIRECT_ELYTRA,
                CommandType.DIRECT_DISENGAGE
        };

        Random rand = new Random();
        CommandType chosen = directCommands[rand.nextInt(directCommands.length)];

        return new CommandSignal(chosen, "Player")
                .withPriority(0.9f)
                .withRationale("Explicit user command");
    }

    // ---- Signal Queue ----

    /**
     * Priority queue for signals, sorted by priority (descending).
     */
    public static class SignalQueue {
        private final PriorityQueue<CommandSignal> queue;

        public SignalQueue() {
            this.queue = new PriorityQueue<>((a, b) -> Float.compare(b.getPriority(), a.getPriority()));
        }

        public void enqueue(CommandSignal signal) {
            queue.offer(signal);
        }

        public void enqueueAll(Collection<CommandSignal> signals) {
            queue.addAll(signals);
        }

        public CommandSignal dequeue() {
            return queue.poll();
        }

        public CommandSignal peek() {
            return queue.peek();
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public int size() {
            return queue.size();
        }

        public List<CommandSignal> drainAll() {
            List<CommandSignal> all = new ArrayList<>(queue);
            queue.clear();
            return all;
        }
    }
}
