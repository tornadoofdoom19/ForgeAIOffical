# ForgeAI Implementation Guide: From Stubs to Runtime

This guide walks developers through completing the remaining stubs and integrating ForgeAI into a live Minecraft server.

---

## Part 1: Event Hook Integration (Enable RL Feedback)

### Current State
- `EventHookRegistry.java` exists with stub implementations
- `CombatEventHandler.java` exists with report methods
- Event wiring is NOT connected to actual Minecraft events

### Step 1.1: Wire Damage Events

**File**: `EventHookRegistry.java` → `registerCombatEventHooks()`

**Completed Code**:
```java
ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
    if (entity instanceof ServerPlayer player) {
        CombatEventHandler.reportPlayerDamage(player, source, amount);
    }
    return true;
});
```

**Status**: ✅ Already implemented in EventHookRegistry.java

### Step 1.2: Wire Death Events

**Completed Code**:
```java
ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
    if (entity instanceof ServerPlayer deadPlayer) {
        CombatEventHandler.reportPlayerKilled(deadPlayer, damageSource);
    } else {
        for (LivingEntity nearby : entity.level().getEntitiesOfClass(
            ServerPlayer.class, entity.getBoundingBox().inflate(32))) {
            if (nearby instanceof ServerPlayer player) {
                CombatEventHandler.reportEnemyKilled(player, entity);
            }
        }
    }
});
```

**Status**: ✅ Already implemented in EventHookRegistry.java

### Step 1.3: Call EventHookRegistry in Mod Init

**File**: `ForgeAI.java`

**Add to `onInitialize()`**:
```java
public class ForgeAI implements ModInitializer {
    @Override
    public void onInitialize() {
        LOGGER.info("ForgeAI initializing...");
        
        // Register RL event hooks
        EventHookRegistry.registerCombatEventHooks();
        EventHookRegistry.registerNavigationEventHooks();
        EventHookRegistry.registerGatheringEventHooks();
        
        LOGGER.info("ForgeAI initialized with event hooks");
    }
}
```

**Impact**: ✅ Enables real damage/death reporting for RL learning

---

## Part 2: Concrete Action Implementation (Enable Task Execution)

### Current State
- Task handlers in `TaskManager.java` exist with `TODO` stubs
- Module execution methods have `TODO` stubs  
- No actual Minecraft player actions (swing, place, use) implemented

### Step 2.1: Implement Basic Player Actions

**Create**: `src/main/java/com/tyler/forgeai/util/PlayerActionUtils.java`

```java
package com.tyler.forgeai.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for executing Minecraft player actions.
 * Allows bots to swing, place blocks, use items, etc.
 */
public class PlayerActionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger("forgeai-actions");

    /**
     * Swing player's held item (attack animation).
     */
    public static void swing(ServerPlayer player, InteractionHand hand) {
        player.swing(hand);
        LOGGER.debug("Player {} swinging {}", player.getName().getString(), hand.name());
    }

    /**
     * Use held item (draw bow, cast fishing rod, activate ability).
     */
    public static void useItem(ServerPlayer player, InteractionHand hand, int ticks) {
        player.startUsingItem(hand);
        
        // Simulate holding button for specified ticks
        for (int i = 0; i < ticks; i++) {
            // Server tick simulation (would be done in game loop in reality)
        }
        
        player.releaseUsingItem();
        LOGGER.debug("Player {} used item for {} ticks", player.getName().getString(), ticks);
    }

    /**
     * Interact with block (open chest, activate button, etc.).
     */
    public static void interactBlock(ServerPlayer player, BlockHitResult hitResult, InteractionHand hand) {
        player.gameMode.useItemOn(player.containerMenu, player.level, 
            player.getItemInHand(hand), hand, hitResult);
        LOGGER.debug("Player {} interacting with block at {}", 
            player.getName().getString(), hitResult.getBlockPos());
    }

    /**
     * Attack/hit entity.
     */
    public static void attackEntity(ServerPlayer player, net.minecraft.world.entity.Entity target) {
        player.attack(target);
        player.swing(InteractionHand.MAIN_HAND);
        LOGGER.debug("Player {} attacking {}", player.getName().getString(), target.getName().getString());
    }

    /**
     * Move in direction at specified speed.
     */
    public static void moveInDirection(ServerPlayer player, float forward, float strafe, boolean jumping) {
        // Simulate client-side input
        player.input.forwardImpulse = forward;
        player.input.leftImpulse = strafe;
        
        if (jumping) {
            player.jump();
        }
    }

    /**
     * Look at specific position.
     */
    public static void lookAt(ServerPlayer player, double x, double y, double z) {
        double dx = x - player.getX();
        double dy = y - (player.getY() + player.getEyeHeight());
        double dz = z - player.getZ();
        
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.001) return;  // Too close
        
        // Calculate pitch and yaw
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        
        player.setRot(yaw, pitch);
        LOGGER.debug("Player {} looking at {}, {}, {}", 
            player.getName().getString(), x, y, z);
    }

    /**
     * Sprint toggle.
     */
    public static void setSprinting(ServerPlayer player, boolean sprinting) {
        player.setSprinting(sprinting);
    }

    /**
     * Jump action.
     */
    public static void jump(ServerPlayer player) {
        player.jump();
    }

    /**
     * Crouch/sneak toggle.
     */
    public static void setCrouching(ServerPlayer player, boolean crouching) {
        player.setShiftKeyDown(crouching);
    }
}
```

**Status**: ✅ Creates foundation for task action implementation

### Step 2.2: Implement Mining Task

**File**: `TaskManager.java` → `executeMine()`

**Replace TODO**:
```java
public static void executeMine(MinecraftServer server, Task task, ServerPlayer player) {
    String ore = task.parameters.getOrDefault("ore", "iron_ore");
    int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
    
    LOGGER.info("Executing mine task: {} x{}", ore, amount);
    
    // Step 1: Locate ore blocks using block scanner
    var oreBlocks = ContextScanner.scanNearbyBlocks(player.level(), player.blockPosition(), 
        64, (block) -> block.getRegistryName().contains(ore));
    
    if (oreBlocks.isEmpty()) {
        LOGGER.warn("No {} blocks found", ore);
        task.status = TaskStatus.FAILED;
        return;
    }
    
    // Step 2: Navigate to nearest ore
    var target = oreBlocks.get(0);
    PathFinder.Path path = PathFinder.findPath(player.blockPosition(), target, player.level());
    
    if (path == null || path.nodes.isEmpty()) {
        LOGGER.warn("Cannot reach {} at {}", ore, target);
        task.status = TaskStatus.FAILED;
        return;
    }
    
    // Step 3: Traverse path
    for (PathFinder.PathNode node : path.nodes) {
        PlayerActionUtils.moveInDirection(player, 1.0f, 0.0f, false);
        // Wait for player to reach node position (would be real tick simulation in practice)
    }
    
    // Step 4: Mine blocks
    int mined = 0;
    for (var block : oreBlocks) {
        if (mined >= amount) break;
        
        // Look at block
        PlayerActionUtils.lookAt(player, 
            block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        
        // Mine block
        PlayerActionUtils.swing(player, InteractionHand.MAIN_HAND);
        // Wait for mining time...
        
        mined++;
        
        // Report to RL
        CombatEventHandler.reportBlockBroken(player, 
            player.level().getBlockState(block), (long)150);  // 150ms = stone mining time
    }
    
    task.status = TaskStatus.COMPLETED;
    LOGGER.info("Mined {} {} blocks", mined, ore);
}
```

**Status**: ⚠️ Requires actual implementation in your context (block scanner, path traversal)

### Step 2.3: Implement Farming Task

**File**: `TaskManager.java` → `executeFarm()`

**Pattern**: Similar to mining
1. Scan for farmland with target crop
2. Navigate to field
3. Break/harvest crops
4. Replant if needed
5. Report completion

**Example for wheat**:
```java
public static void executeFarm(MinecraftServer server, Task task, ServerPlayer player) {
    String crop = task.parameters.getOrDefault("crop", "wheat");
    int amount = Integer.parseInt(task.parameters.getOrDefault("amount", "1"));
    
    // Find wheat (age=7 is mature)
    var crops = ContextScanner.scanNearbyBlocks(player.level(), player.blockPosition(), 
        128, (block) -> block.getBlockData().contains("wheat") && 
            Integer.parseInt(block.getBlockData().get("age")) >= 7);
    
    // Harvest and replant logic...
    // Each harvest = swing attack, drop collection
    // Each plant = look down, use block interaction to plant seed
}
```

---

## Part 3: Schematic File Parsing (Enable Building)

### Current State
- `LitematicaIntegration.loadSchematic()` exists but returns stub data
- No actual `.litematic` file parsing implemented

### Step 3.1: Parse NBT Format

**File**: `LitematicaIntegration.java` → `loadSchematic()`

The `.litematic` format is NBT-compressed. Parsing requires:

```java
public static SchematicData loadSchematic(File schematicFile) throws Exception {
    // Use Minecraft's NBT API
    net.minecraft.nbt.CompoundTag rootTag = 
        net.minecraft.nbt.NbtIo.read(schematicFile);
    
    // Extract schematic metadata
    var metadata = rootTag.getCompound("Metadata");
    String name = metadata.getString("Name");
    
    // Extract block list (Litematica uses specific tag structure)
    var blockRegions = rootTag.getList("Regions", net.minecraft.nbt.Tag.TAG_COMPOUND);
    
    SchematicData schematic = new SchematicData(name);
    
    for (int i = 0; i < blockRegions.size(); i++) {
        var region = blockRegions.getCompound(i);
        var blockStates = region.getList("BlockStates", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY);
        
        // Parse block states into block positions and names
        // ... (complex bit unpacking - see Litematica source)
    }
    
    return schematic;
}
```

**Note**: Full NBT parsing is complex. Consider using Litematica's API directly if available.

### Step 3.2: Build Material List

**Status**: ✅ Already implemented in `computeMaterialRequirements()`

**Callable code**:
```java
Map<String, Integer> materials = LitematicaIntegration.computeMaterialRequirements(schematic);
// materials = {oak_log: 45, oak_planks: 120, stone: 80, ...}
```

### Step 3.3: Execute Build Task

**File**: `TaskManager.java` → `executeBuild()`

**Pattern**:
```java
public static void executeBuild(MinecraftServer server, Task task, ServerPlayer player) {
    String schematicName = task.parameters.getOrDefault("schematic", "structure");
    File schematicFile = new File("schematics/" + schematicName + ".litematic");
    
    // Load schematic
    LitematicaIntegration.SchematicData schematic = 
        LitematicaIntegration.loadSchematic(schematicFile);
    
    if (schematic == null) {
        task.status = TaskStatus.FAILED;
        return;
    }
    
    // Compute materials needed
    Map<String, Integer> materials = 
        LitematicaIntegration.computeMaterialRequirements(schematic);
    
    // Assign gatherer bots to collect materials
    BotRegistry registry = BotRegistry.getOrCreateRegistry(player.level());
    var gatherBots = registry.discoverBots(
        bot -> !bot.isCurrentlyBuilding());
    
    // Create gathering subtasks for each material
    for (var entry : materials.entrySet()) {
        Task gatherTask = new Task(
            "gather_" + entry.getKey(),
            AICommandParser.CommandType.TASK_GATHER,
            TaskManager.TaskPriority.HIGH,
            Map.of("resource", entry.getKey(), "amount", String.valueOf(entry.getValue()))
        );
        
        // Dispatch to available bot
        if (!gatherBots.isEmpty()) {
            gatherBots.get(0).getTaskManager().queueTask(
                AICommandParser.parseCommand("gather " + entry.getKey() + " " + entry.getValue()),
                TaskManager.TaskPriority.HIGH
            );
        }
    }
    
    // Build main structure
    BlockPos origin = player.blockPosition().offset(0, 1, 0);
    
    LitematicaIntegration.SchematicData.BlockEntry nextBlock;
    while ((nextBlock = LitematicaIntegration.getNextBlockToBuild(
        player.level(), schematic, origin)) != null) {
        
        // Navigate to block position
        BlockPos blockPos = origin.offset(nextBlock.pos.getX(), 
            nextBlock.pos.getY(), nextBlock.pos.getZ());
        
        // Place block (requires having it in inventory)
        // ... placement logic using BlockInteractionUtils or direct methods
    }
    
    task.status = TaskStatus.COMPLETED;
}
```

---

## Part 4: Integration Testing Checklist

### Pre-Test Setup
- [ ] Java 21 JDK installed (or Java 11+ if using older Minecraft)
- [ ] Gradle build successful: `./gradlew build`
- [ ] Fabric/Loom configured for your Minecraft version
- [ ] Mod jar generated: `build/libs/forgeai-*.jar`

### Test 1: Event Hook Wiring
**Objective**: Verify RL feedback system receives events

**Steps**:
1. Place bot in Minecraft world
2. Hit bot with sword (2 hearts damage)
3. Check logs for: `reportPlayerDamage() called with amount=4`
4. Verify RewardSystem logs: `PUNISHMENT: 0.04`
5. Check that future combat decisions reduce sword defense usage

**Expected Output**:
```
[ForgeAI] reportPlayerDamage() called: player=Bot1, amount=4.0
[ForgeAI] PUNISHMENT applied: 0.04 (avoid similar situations)
[ForgeAI] Training weights adjusted: sword_defense -0.02
```

### Test 2: Basic Task Execution
**Objective**: Verify bots can execute simple tasks

**Steps**:
1. Issue command: `/forgeai mine iron_ore 10`
2. Bot should navigate to iron ore and mine it
3. Check task queue processing and completion

**Expected Output**:
```
[ForgeAI] Task queued: mine_iron_ore (priority=NORMAL)
[ForgeAI] Started task: mine_iron_ore
[ForgeAI] Navigating to iron_ore...
[ForgeAI] Mining ore at X=100 Y=64 Z=200
[ForgeAI] Collected iron_ore x10
[ForgeAI] Completed task: mine_iron_ore
```

### Test 3: Multi-Bot Coordination
**Objective**: Verify bots can coordinate on jobs

**Steps**:
1. Issue command: `/forgeai job create build farm_house /path/to/schematic.litematic`
2. Verify BotDispatcher creates job and assigns subtasks
3. Check bot registry discovers available builders
4. Monitor job progress tracking

**Expected Output**:
```
[ForgeAI] Job created: build_farm_house (5 subtasks)
[ForgeAI] Subtask 1: gather oak_log x45 → Bot2
[ForgeAI] Subtask 2: gather stone x80 → Bot3
[ForgeAI] Job progress: 20% complete
```

### Test 4: Nighttime Sleep
**Objective**: Verify bots sleep at night

**Steps**:
1. Create bot with initialized NightSleepHandler
2. Advance time to nighttime (game command `/time set 14000`)
3. Check that bot finds bed and sleeps
4. Monitor logs for sleep/wake notifications

**Expected Output**:
```
[ForgeAI] Nighttime detected (tick=14500)
[ForgeAI] Bed found at X=100 Y=64 Z=200
[ForgeAI] Sleeping... ZZZ
[ForgeAI] Morning arrived (tick=0)
[ForgeAI] Woke up, resuming tasks
```

### Test 5: Chest Management
**Objective**: Verify bots can trade items via chests

**Steps**:
1. Create two bots (Bot1 gathering, Bot2 building)
2. Place neutral chest at meeting point
3. Bot1 should deposit gathered items
4. Bot2 should withdraw items from chest
5. Verify ChestManager caching and transfers

**Expected Output**:
```
[ForgeAI] Bot1 depositing: oak_log x20 → chest at X=50 Y=64 Z=200
[ForgeAI] Chest inventory: oak_log (20)
[ForgeAI] Bot2 withdrawing: oak_log x20 → inventory
[ForgeAI] Chest inventory: empty
```

---

## Part 5: Debugging & Logging

### Enable Debug Mode

**File**: `ForgeAI.java` or `logback.xml`

```java
// In test harness or init
LOGGER.setLevel(Level.DEBUG);
```

### Trace Specific Systems

```java
// RL Feedback
CombatEventHandler.enableDebugLogging(true);

// Task Execution
TaskManager.LOGGER.setLevel(Level.DEBUG);

// Navigation
PathFinder.LOGGER.setLevel(Level.DEBUG);

// Event Hooks
EventHookRegistry.LOGGER.setLevel(Level.DEBUG);
```

### Common Debug Points

1. **RL not learning**: Check `CombatEventHandler.reportPlayerDamage()` is being called
2. **Tasks not executing**: Check `TaskManager.tick()` is being called each game tick
3. **Navigation failing**: Check `PathFinder.findPath()` is finding valid paths
4. **Multi-bot not coordinating**: Check `BotRegistry` has all bots registered

---

## Part 6: Performance Tuning

### Memory Optimization

```java
// Limit shared memory cache size
SharedWorldMemory memory = new SharedWorldMemory();
memory.setMaxCacheSize(1000);  // Cache only 1000 blocks

// Disable block caching for distant regions
memory.setCacheRadius(64);  // Only cache 64 blocks around bot
```

### Computation Optimization

```java
// Cache pathfinding results
PathFinder.enablePathCache(true);

// Limit simultaneous tasks per bot
TaskManager.setMaxConcurrentTasks(1);

// Reduce decision update rate
DecisionEngine.setDecisionTickRate(10);  // Decide every 10 ticks, not every tick
```

### Scaling to 10+ Bots

```java
// Offload RL training to background thread
TrainingManager.enableBackgroundThreading(true);

// Batch process events
CombatEventHandler.enableEventBatching(true);
CombatEventHandler.setBatchSize(100);  // Process events in batches of 100

// Reduce bot discovery frequency
BotRegistry.setDiscoveryInterval(100);  // Discover bots every 100 ticks, not every tick
```

---

## Summary of Implementation Work

| Component | Status | Implementation Time | Dependencies |
|-----------|--------|-------------------|--------------|
| Event Hooks | ✅ Partial | 1 hour | Minecraft event API |
| Player Actions | ⚠️ Stubs | 4 hours | Player input simulation |
| Mining Task | ⚠️ Stubs | 2 hours | Block scanner, pathfinding |
| Farming Task | ⚠️ Stubs | 2 hours | Block scanner, pathfinding |
| Schematic Parsing | ⚠️ Stubs | 3 hours | NBT file format knowledge |
| Build Task | ⚠️ Stubs | 2 hours | All above completed |
| Testing Suite | ⚠️ Partial | 2 hours | All above completed |
| **Total** | | **~16 hours** | |

---

## Next Steps

1. **Immediate** (1-2 weeks):
   - [ ] Implement concrete player action methods
   - [ ] Finish task handler implementations (mine, farm, chop, etc.)
   - [ ] Complete NBT schematic file parsing
   - [ ] Run Test 1-3 integration tests

2. **Short-term** (2-4 weeks):
   - [ ] Complete remaining task types (enchant, brew, etc.)
   - [ ] Optimize pathfinding cache
   - [ ] Multi-bot stress testing (5+ bots)
   - [ ] Performance profiling and tuning

3. **Medium-term** (1-2 months):
   - [ ] Advanced PvP tactics (multi-opponent, armor prediction)
   - [ ] Schematic building optimization
   - [ ] RL learning curve analysis
   - [ ] Public release and community feedback

---

For questions or issues, refer to:
- [EVENT_HOOK_INTEGRATION_GUIDE.md](EVENT_HOOK_INTEGRATION_GUIDE.md)
- [INTEGRATION_COMPLETE_SUMMARY.md](INTEGRATION_COMPLETE_SUMMARY.md)
- [docs/architecture.md](docs/architecture.md)
