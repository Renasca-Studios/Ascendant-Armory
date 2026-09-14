# Ascension Cores Test Scenarios

Run these from `c:\Git\Ascension Cores`.

## Build

```powershell
.\gradlew.bat build
```

Expected: build succeeds. Java 26 may print native-access warnings from Gradle.

## Core Textures

```powershell
python tmp\generate_core_textures.py
```

Expected files:

- `src/main/resources/assets/ascensioncores/textures/item/ascension_core.png`
- `src/main/resources/assets/ascensioncores/textures/item/chaos_core.png`

Launch the client and confirm both cores have item icons in the Ingredients creative tab.

## Tooltip And Stat Pool

1. Start a creative world.
2. Hold a gear item, for example a diamond sword.
3. Run:

```mcfunction
/ascensioncores level set 4
```

Expected: tooltip shows `Level: 4`, four rolled stats, and vanilla item lines still visible. Ascension-specific custom stats can appear, including `Crit Damage`, `Execute Damage`, `Ambush Damage`, `Last Stand Guard`, and `Steady Guard`. Enchantment-overlap rolls like Fortune, Knockback, Thorns-style reflection, Feather Falling-style fall reduction, and Efficiency-style mining speed should not appear.

## Custom Crit Damage

1. Hold a weapon and run:

```mcfunction
/ascensioncores reroll
```

2. Repeat until the tooltip includes `Crit Damage`, or use `/ascensioncores level set 4` first for more stat slots.
3. Spawn a target:

```mcfunction
/summon minecraft:zombie ~ ~ ~
```

Expected: only vanilla critical hits get the Ascension crit damage bonus. Normal grounded hits should not get that bonus.

## Custom Conditional Stats

Use `/ascensioncores reroll` on level 4 gear until the named stat appears.

Weapon checks:

- `Execute Damage`: hit a target below 35% health. Expected: bonus applies only to low-health targets.
- `Ambush Damage`: hit a target from behind. Expected: bonus applies only when the attacker is behind the target's facing direction.

Armor checks:

- `Last Stand Guard`: wear the armor and take damage while below 35% health. Expected: incoming damage is reduced only at low health.
- `Steady Guard`: wear the armor and crouch while taking damage. Expected: incoming damage is reduced only while crouching.

## Natural Leveled Gear Loot

For a fast deterministic check, temporarily edit `config/ascensioncores.properties`:

```properties
naturalLeveledGearChance=1.0000
naturalLeveledGearLevel2Chance=1.0000
naturalLeveledGearLevel3Chance=1.0000
naturalLeveledGearLevel4Chance=1.0000
```

Then run:

```mcfunction
/ascensioncores reload
/loot give @s loot minecraft:chests/end_city_treasure
/loot give @s loot minecraft:chests/ancient_city
/loot give @s loot minecraft:chests/trial_chambers/reward_unique
```

Expected: each loot command has a high chance to give leveled gear. Item level should never exceed the material capacity, so iron caps below diamond/netherite.

Reset the config values afterward:

```properties
naturalLeveledGearChance=0.0080
naturalLeveledGearLevel2Chance=0.1400
naturalLeveledGearLevel3Chance=0.0300
naturalLeveledGearLevel4Chance=0.0050
```

## Ascension Core Chest Loot

For a fast deterministic check, temporarily edit `config/ascensioncores.properties`:

```properties
levelCoreChestChance=1.0000
levelCoreChestMinDrop=1
levelCoreChestMaxDrop=2
```

Then run:

```mcfunction
/ascensioncores reload
/loot give @s loot minecraft:chests/simple_dungeon
/loot give @s loot minecraft:chests/abandoned_mineshaft
/loot give @s loot minecraft:chests/shipwreck_supply
/loot give @s loot minecraft:chests/trial_chambers/supply
```

Expected: each command gives 1-2 Ascension Cores from the added chest pool.

Reset the config values afterward:

```properties
levelCoreChestChance=0.0800
levelCoreChestMinDrop=1
levelCoreChestMaxDrop=2
```

## Anvil Upgrade And Chaos

1. Put gear in anvil slot 0.
2. Put Ascension Cores in slot 1.
3. Take the result.

Expected: gear level increases, cost scales by config, and vanilla attributes remain visible.

For same-item durability repair:

1. Damage two items of the same gear type. They may be un-leveled or ascended.
2. Put them in anvil slots 0 and 1.
3. Take the repaired result.

Expected: vanilla durability repair remains available. Trait donation only takes over when
the same-item combination has no valid vanilla result.

For Chaos Core:

1. Put level 1+ gear in anvil slot 0.
2. Put a Chaos Core in slot 1.
3. Remove and reinsert the Chaos Core several times.

Expected: preview shows the next rolled stats and stays the same when removing/reinserting the Chaos Core or gear. Taking the output applies exactly that preview.

## Rare Core Drops

For a fast check, temporarily set:

```properties
mobAscensionCoreDropChance=1.0000
fullyArmoredMobAscensionCoreDropChance=1.0000
mobChaosCoreDropChance=1.0000
```

Then run:

```mcfunction
/ascensioncores reload
/summon minecraft:zombie ~ ~ ~ {ArmorItems:[{id:"minecraft:iron_boots",count:1},{id:"minecraft:iron_leggings",count:1},{id:"minecraft:iron_chestplate",count:1},{id:"minecraft:iron_helmet",count:1}],HandItems:[{id:"minecraft:iron_sword",count:1},{}]}
```

Kill the zombie.

Expected: Ascension Cores and a Chaos Core drop. Reset the config after testing.

## Better Vanilla Mobs Compatibility

Install Better Vanilla Mobs, or summon a tagged stand-in:

```mcfunction
/summon minecraft:zombie ~ ~ ~ {Tags:["fkbm.touched"]}
```

For a fast deterministic check, temporarily set:

```properties
betterVanillaMobsAscensionCoreDropChance=1.0000
betterVanillaMobsChaosCoreDropChanceBonus=1.0000
```

Then run:

```mcfunction
/ascensioncores reload
/summon minecraft:zombie ~ ~ ~ {Tags:["fkbm.touched"]}
```

Kill the zombie.

Expected: the enhanced mob can drop Ascension Cores even with no visible gear. For alpha checks, use `Tags:["fkbm.touched","fkbm.alpha"]` and the `betterVanillaMobsAlpha*` config values.

## Automated Headless Game Tests

The normal build launches a dedicated Minecraft GameTest server and verifies same-item
anvil durability repair, preservation of all Netherite base modifiers, stack-specific
modifier precedence, and repair of previously missing prototype modifiers:

```powershell
.\gradlew.bat clean build
```

Run the same suite against the 26.1.2 backport with:

```powershell
.\gradlew.bat clean build '-Pminecraft_version=26.1.2' '-Ploader_version=0.19.5' '-Pfabric_api_version=0.149.0+26.1.2' '-Pjei_minecraft_version=26.1.2' '-Pjei_version=29.5.0.26' '-Pminecraft_dependency=~26.1'
```
