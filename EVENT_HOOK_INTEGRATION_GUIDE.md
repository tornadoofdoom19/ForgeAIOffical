# Event Hook Integration Guide

This document describes how to wire ForgeAI's RL feedback system to actual Minecraft events.

## Overview

ForgeAI uses an event-driven architecture where gameplay events (damage, kills, teleports, block breaks) are reported to the RL system for learning. The `CombatEventHandler` provides methods for reporting these events, and `EventHookRegistry` coordinates registration with Minecraft's event bus.

## Event Flow

```
Minecraft Event (damage, death, teleport)
    ↓
Event Hook (registered in EventHookRegistry)
    ↓
CombatEventHandler Report Method (reportPlayerDamage, reportPlayerKilled, etc.)
    ↓
RL System (RewardSystem, PunishmentSystem) receives reward/punishment
    ↓
TrainingManager updates weights for decision combos
```

## Implemented Event Hooks

### 1. Player Damage Event (ServerLivingEntityEvents.ALLOW_DAMAGE)
- **Trigger**: When player takes damage
- **Handler**: `CombatEventHandler.reportPlayerDamage(player, source, amount)`
- **Feedback**: Negative reward (punishment) based on damage taken
- **RL Use**: Reinforces avoidance of situations that caused damage

**Example**:
```
Player takes 4 hearts from opponent sword
→ reportPlayerDamage() called with amount=8 half-hearts
→ RewardSystem.punish() called with amount/100 = 0.08
→ Sword defense training decreased, shield/totem usage increased
```

### 2. Player Death Event (ServerLivingEntityEvents.AFTER_DEATH)
- **Trigger**: When player dies
- **Handler**: `CombatEventHandler.reportPlayerKilled(player, damageSource)`
- **Feedback**: Large negative reward (severe punishment)
- **RL Use**: Strongly reinforces avoiding the situation that caused death

**Example**:
```
Player dies to mob fall damage
→ reportPlayerKilled() called
→ RewardSystem.punish() called with amount=1.0 (max penalty)
→ Navigation module heavily punished, elytra usage increased for fall prevention
```

### 3. Enemy Kill Event (mob/player kill)
- **Trigger**: When nearby entity dies (mob or player)
- **Handler**: `CombatEventHandler.reportEnemyKilled(player, killedEntity)`
- **Feedback**: Positive reward based on entity type/difficulty
- **RL Use**: Reinforces combat strategies that led to the kill

**Example**:
```
Player kills opponent player in PvP
→ reportEnemyKilled() called with ServerPlayer entity
→ RewardSystem.reward() called with amount=0.5 (significant reward)
→ Active PvP module (sword/mace/bow) training increased
→ Combo weights adjusted to favor what was active during kill
```

## Unimplemented Event Hooks (Stub Locations)

### 4. Portal/Teleport Events
**File**: `EventHookRegistry.registerNavigationEventHooks()`
**TODO**: 
- Register EntityTeleportEvent (ender pearl, teleport command)
- Register EnderPearl projectile hit event
- Track teleport distance and success
- Reward successful evasion teleports

**Expected Hook**:
```java
// When entity teleports
CombatEventHandler.reportTeleport(player, fromPos, toPos, success);
```

### 5. Block Break Events
**File**: `EventHookRegistry.registerGatheringEventHooks()`
**TODO**:
- Register BlockBreakEvent (mining)
- Track block type, mining time
- Reward task completion (mining X ore)
- Track tool efficiency

**Expected Hook**:
```java
// When player breaks block
CombatEventHandler.reportBlockBroken(player, block, time);
```

### 6. Block Place Events
**TODO**:
- Register BlockPlaceEvent (building)
- Track placement progress for schematics
- Reward correct placements, punish wrong ones
- Enable building task RL

**Expected Hook**:
```java
// When player places block
CombatEventHandler.reportBlockPlaced(player, block, correct);
```

### 7. Combat Ability Events
**TODO**:
- Shield break detection
- Totem activation detection
- Critical hit detection (sword/mace)
- Web trap success detection

**Expected Hooks**:
```java
CombatEventHandler.reportShieldBroken(player);
CombatEventHandler.reportTotemActivated(player);
CombatEventHandler.reportCriticalHit(player, damage);
CombatEventHandler.reportWebTrapSuccess(player, victim);
```

## Integration Steps

### Step 1: Register Event Hooks at Mod Initialization
In `ForgeAI.java` mod init:
```java
@Override
public void onInitialize() {
    // ... existing code ...
    
    // Register RL event hooks
    EventHookRegistry.registerCombatEventHooks();
    EventHookRegistry.registerNavigationEventHooks();
    EventHookRegistry.registerGatheringEventHooks();
    
    LOGGER.info("ForgeAI initialized with event hooks");
}
```

### Step 2: Connect CombatEventHandler to Bots
When creating a DecisionEngine/bot:
```java
DecisionEngine engine = new DecisionEngine("MyBot");
RewardSystem rewards = new RewardSystem();
PunishmentSystem punishments = new PunishmentSystem();

// Wire event handler to RL systems
CombatEventHandler.setRewardSystem(rewards);
CombatEventHandler.setPunishmentSystem(punishments);
CombatEventHandler.setTrainingManager(trainingManager);

engine.setRewardSystem(rewards);
engine.setPunishmentSystem(punishments);
```

### Step 3: Emit Custom Events from Task Handlers
In `TaskManager.executeMine()`, `executeBuild()`, etc.:
```java
public static void executeMine(MinecraftServer server, Task task, ServerPlayer player) {
    // ... mining logic ...
    
    // After successfully mining ore:
    CombatEventHandler.reportBlockBroken(player, oreBlock, elapsedTime);
    
    // RL system rewards mining task completion
}
```

## RL Reward/Punishment Values

### Damage Taken (Negative)
- Punishment = damageAmount / 100
- Range: 0.0 (half-heart) to 1.0 (50 hearts)
- Typical: 4 hearts = 0.08 punishment

### Player Kill (Positive)
- Reward = 0.5 (significant)
- Modifier: +0.25 if opponent was stronger (armor/health)
- Modifier: +0.1 per nearby threats (dangerous kill)

### Enemy Kill (mob)
- Reward = 0.1-0.3 based on mob difficulty
- Creeper/Skeleton: 0.1
- Enderman: 0.2
- Dragon/Wither: 0.5+

### Task Completion (Positive)
- Mine ore: +0.05 per ore block
- Farm crops: +0.02 per crop
- Build block: +0.01 per correct block
- Total schematic: +0.3 completion bonus

### Task Failure (Negative)
- Wrong block placed: -0.05
- Task timeout: -0.1
- Resource lost: -0.1 to -0.3

## Testing Event Integration

After implementing event hooks, test with:

1. **Damage Test**: Hit bot with sword, verify RL punishment increases shield/totem training
2. **Kill Test**: Kill bot with fire, verify RL reward increases fire-evasion training
3. **Mining Test**: Mine ore block, verify task reward feedback system
4. **Building Test**: Place correct/wrong blocks, verify schematic feedback

## Debugging

Enable trace logging in CombatEventHandler:
```java
CombatEventHandler.enableDebugLogging(true);
```

This will log:
- Every event received
- RL reward/punishment applied
- Training weight changes
- Combo selections influenced by recent events

## See Also

- [CombatEventHandler.java](src/main/java/com/tyler/forgeai/core/CombatEventHandler.java)
- [RewardSystem.java](src/main/java/com/tyler/forgeai/ai/RewardSystem.java)
- [PunishmentSystem.java](src/main/java/com/tyler/forgeai/ai/PunishmentSystem.java)
- [DecisionEngine.java](src/main/java/com/tyler/forgeai/core/DecisionEngine.java)
