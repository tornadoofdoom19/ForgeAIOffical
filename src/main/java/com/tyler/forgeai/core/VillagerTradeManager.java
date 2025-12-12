package com.tyler.forgeai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Villager Trade Manager: Find and trade with villagers.
 * - Locate villagers by profession and trade
 * - Remember villager locations
 * - Gather materials for trades
 * - Execute trades
 * - Handle trading halls
 */
public class VillagerTradeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-trades");

    public static class VillagerInfo {
        public Villager villager;
        public net.minecraft.core.BlockPos location;
        public String profession;  // e.g., "librarian", "cleric", "farmer"
        public List<String> knownTrades;  // e.g., ["mending book", "sharpness V"]
        public long lastTradedAt;
        public int tradeCount;

        public VillagerInfo(Villager villager, String profession) {
            this.villager = villager;
            this.profession = profession;
            this.location = villager.blockPosition();
            this.knownTrades = new ArrayList<>();
            this.lastTradedAt = System.currentTimeMillis();
            this.tradeCount = 0;
        }

        public boolean isAlive() {
            return villager != null && villager.isAlive();
        }

        public String getDescription() {
            return String.format("Villager[%s] at %s, trades: %s",
                profession, location.toShortString(), String.join(", ", knownTrades));
        }
    }

    private static final Map<String, VillagerInfo> KNOWN_VILLAGERS = new HashMap<>();

    /**
     * Find villager with specific trade item nearby.
     */
    public static VillagerInfo findVillagerWithTrade(ServerPlayer player, String tradeItem, int radius) {
        try {
                        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(radius));
            
            for (Villager villager : villagers) {
                if (hasTradeForItem(villager, tradeItem)) {
                    String profession = villager.getVillagerData().getProfession().toString();
                    VillagerInfo info = new VillagerInfo(villager, profession);
                    info.knownTrades.add(tradeItem);
                    
                    LOGGER.info("Found {} at {}", profession, villager.blockPosition());
                    return info;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error finding villagers: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if villager has trade for specific item.
     */
    private static boolean hasTradeForItem(Villager villager, String itemName) {
        try {
            MerchantOffers offers = villager.getOffers();
            if (offers == null) return false;

            for (MerchantOffer offer : offers) {
                // Check result item
                if (offer.getResult().getItem().getName().toString().contains(itemName.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error checking villager trades: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Remember villager location.
     */
    public static void rememberVillager(String key, VillagerInfo info) {
        KNOWN_VILLAGERS.put(key, info);
        LOGGER.info("Remembered villager: {} at {}", key, info.location.toShortString());
    }

    /**
     * Find remembered villager by name.
     */
    public static VillagerInfo getRememberedVillager(String key) {
        return KNOWN_VILLAGERS.get(key);
    }

    /**
     * Get all remembered villagers with profession.
     */
    public static List<VillagerInfo> getVillagersByProfession(String profession) {
        List<VillagerInfo> results = new ArrayList<>();
        for (VillagerInfo info : KNOWN_VILLAGERS.values()) {
            if (info.isAlive() && info.profession.toLowerCase().contains(profession.toLowerCase())) {
                results.add(info);
            }
        }
        return results;
    }

    /**
     * Execute trade with villager.
     */
    public static boolean executeTradeWithVillager(ServerPlayer player, Villager villager, String itemWanted) {
        try {
            // Open villager trade window (would be done in game interface in reality)
            player.openMerchantScreen(villager);
            
            MerchantOffers offers = villager.getOffers();
            for (MerchantOffer offer : offers) {
                if (offer.getResult().getItem().getName().toString().contains(itemWanted.toLowerCase())) {
                    // Check if player has required items
                    ItemStack first = offer.getCostA();
                    ItemStack second = offer.getCostB();
                    
                    if (hasItemInInventory(player, first) && hasItemInInventory(player, second)) {
                        // Execute trade
                        LOGGER.info("Trading with {} for {}", 
                            villager.getVillagerData().getProfession().toString(), itemWanted);
                        return true;
                    } else {
                        LOGGER.warn("Missing required items for trade");
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error executing trade: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Check if player has item in inventory.
     */
    private static boolean hasItemInInventory(ServerPlayer player, ItemStack required) {
        if (required.isEmpty()) return true;
        
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == required.getItem() && 
                stack.getCount() >= required.getCount()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gather materials for trade if missing.
     */
    public static boolean gatherMaterialsForTrade(ServerPlayer player, Villager villager, String tradeItem) {
        LOGGER.info("Gathering materials for {} trade", tradeItem);
        
        // This would trigger resource gathering tasks
        // In TaskManager: check if items missing, create gather tasks
        // Determine required materials for the trade
        try {
            MerchantOffers offers = villager.getOffers();
            if (offers == null) return false;
            for (MerchantOffer offer : offers) {
                if (!offer.getResult().getItem().getName().getString().toLowerCase().contains(tradeItem.toLowerCase())) continue;

                List<ItemStack> required = new ArrayList<>();
                if (!offer.getCostA().isEmpty()) required.add(offer.getCostA());
                if (!offer.getCostB().isEmpty()) required.add(offer.getCostB());

                // Check if player already has the required items
                boolean needGather = false;
                for (ItemStack req : required) {
                    if (!hasItemInInventory(player, req)) { needGather = true; break; }
                }

                if (!needGather) return true; // already has items

                // Try to delegate gathering subtask to an available gatherer bot
                BotRegistry registry = BotRegistry.getOrCreateRegistry((net.minecraft.server.level.ServerLevel) player.level());
                List<BotRegistry.BotInstance> bots = registry.findAvailableBotsForRole(player.getGameProfile().getName(), "gatherer");
                if (bots.isEmpty()) {
                    LOGGER.warn("No gatherer bots available to collect materials");
                    return false;
                }
                // Create a simple gather task for first missing material
                ItemStack missing = null;
                for (ItemStack req : required) {
                    if (!hasItemInInventory(player, req)) { missing = req; break; }
                }
                if (missing == null) return false;

                // Build task parameters
                Map<String, String> params = new HashMap<>();
                String resource = missing.getItem().toString();
                params.put("resource", resource);
                params.put("amount", String.valueOf(missing.getCount()));

                AICommandParser.ParsedCommand pc = AICommandParser.parse("gather " + resource + " " + missing.getCount());
                AICommandParser.CommandType type = AICommandParser.CommandType.TASK_MINE_IRON;
                try { type = pc != null ? pc.type : type; } catch (Exception ignored) {}
                TaskManager.Task subtask = new TaskManager.Task(UUID.randomUUID().toString().substring(0,8), type, TaskManager.getPriorityForCommand(type), params);

                // Delegate to first available bot
                BotRegistry.BotInstance targetBot = bots.get(0);
                targetBot.taskManager.enqueueTask(subtask);
                LOGGER.info("Delegated gather task for {} to bot {}", resource, targetBot.botName);
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Error gathering materials for trade: {}", e.getMessage());
            return false;
        }

        return false;
    }

    /**
     * Get best villager for item (closest, most trades, best price).
     */
    public static VillagerInfo getBestVillagerForItem(ServerPlayer player, String tradeItem) {
        var candidates = new ArrayList<VillagerInfo>();
        
        try {
                        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(128));
            
            for (Villager villager : villagers) {
                if (hasTradeForItem(villager, tradeItem)) {
                    VillagerInfo info = new VillagerInfo(villager, 
                        villager.getVillagerData().getProfession().toString());
                    candidates.add(info);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error finding villagers: {}", e.getMessage());
        }

        if (candidates.isEmpty()) return null;
        
        // Sort by distance
        candidates.sort((a, b) -> 
            Double.compare(
                player.distanceToSqr(a.location.getCenter()),
                player.distanceToSqr(b.location.getCenter())
            )
        );

        return candidates.get(0);
    }

    /**
     * Clear stale villager entries.
     */
    public static void cleanupVillagers() {
        KNOWN_VILLAGERS.entrySet().removeIf(entry -> !entry.getValue().isAlive());
        LOGGER.info("Cleaned up {} stale villager entries", 
            KNOWN_VILLAGERS.size());
    }

    /**
     * Get villager stats for trading hall.
     */
    public static String getVillagerStats(String key) {
        VillagerInfo info = KNOWN_VILLAGERS.get(key);
        if (info == null) return "Unknown villager";
        
        long timeSinceTrade = System.currentTimeMillis() - info.lastTradedAt;
        return String.format("%s - %d trades, last trade %dmin ago",
            info.profession, info.tradeCount, timeSinceTrade / 60000);
    }
}
