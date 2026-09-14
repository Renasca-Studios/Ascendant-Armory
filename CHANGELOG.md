## 1.4.1

### Fixed
- **Anvil durability repair.** Combining matching gear now preserves vanilla durability repair instead of being incorrectly blocked by trait donation handling.
- **Item attribute preservation.** Ascending gear now retains every original attribute, including Netherite toughness and knockback resistance and attributes supplied by other mods. Existing affected gear is repaired on player login, and stack-specific modifier values remain authoritative over item defaults.

### Changed
- **Minecraft 26.2 support.** Added a 26.2 build while keeping a separate 26.1.2 backport available.

## 1.4.0

### Added
- **Advancements + kill tracking.** Added a full Ascension advancement tree and per-item kill counters for leveled weapons.
- **Trait synergies.** Cryoexecution, Plague Doctor, and Stormbreaker reward specific trait pairs with +25% conditional damage.
- **Curse traits.** Trait rolls can now hit a cursed sub-pool (`curse_frail`, `curse_sluggish`, `curse_brittle`, `curse_weak`) with direct downsides. Configurable via `enableCurseTraits` and `curseChance`.
- **Loot tier tuning.** Added `lootLevelBumpChance` and `treasureLootLevelBumpChance` to control high-tier pre-leveled loot rarity.

### Changed
- **Loot distribution rework.** Pre-leveled loot now uses a cleaner truncated geometric formula, making Divine properly rarer than Mythic.
- **Removed Deflection.** Evasion now covers the useful projectile-negation niche; old Deflection slots are repaired automatically when a player's inventory loads.
- **Trait-gap repair.** Removed/invalid trait IDs are dropped and refilled once on player join so old gear keeps its unlocked trait count.
- **Cleaner tooltips.** Removed noisy next-level trait previews from normal inventory tooltips.

## 1.3.0

### Added
- Added native Warband compatibility for mob core drops.
- **Warband** support (upcoming mod, might be out by the time you read this)
  - Warband-stamped mobs now add Ascension Core drop chance based on Warband difficulty.
  - Warband squad members receive a small additional Ascension Core drop bonus.
  - Warband leaders can add a Chaos Core drop bonus based on Warband difficulty.
  - Farm-suppressed Warband mobs are excluded from Warband-based core drop bonuses.
  - Added config controls for Warband integration:
    - `enableWarbandIntegration`
    - `warbandMinimumDifficulty`
    - `warbandAscensionCoreBaseChance`
    - `warbandAscensionCoreDifficultyChance`
    - `warbandSquadRoleAscensionCoreBonus`
    - `warbandChaosCoreDifficultyChance`

### Fixed
- Fixed Hostile Mobs Improve Over Time compatibility so drop scaling only applies to mobs actually tagged as improved by the datapack.
- Fixed Consuming Speed applying far more strongly than its tooltip percentage implied.
- Fixed Experience Bonus, Natural Regeneration, and Repair Discount using flat dynamic modifiers instead of true percentage modifiers.
- Existing ascended gear now periodically rebuilds outdated Ascension attribute modifiers when held, carried, or equipped by a player.

## 1.2.0

### Added
- **Hostile Mobs Improve Over Time** support — Ascension and Chaos Core drop rates now scale with that datapack's per-player difficulty score. Soft integration; no effect if the datapack isn't installed.
- New config: `enableHostileMobsImproveIntegration`, plus per-level drop-chance tuning.

## 1.1.0

### Added
- **Gear Salvage** — place leveled gear alone in an anvil (no second item, no rename) to break it down and recover a portion of the Ascension Cores invested in it. Move your investment to a better base item instead of stranding cores in old gear.
- **Chaos Gamble Mode** — optional config flag. Makes Chaos Core rerolls swingier: traits can roll above their normal maximum, or bust to the minimum.
- New config: `enableSalvage`, `salvageRefundPercent`, `chaosGambleMode`, `enableEnchantmentSlots` (toggle the whole enchantment-slot system).

### Fixed
- Enchantment-slot cap was never enforced on un-ascended gear — enchanted books could be slapped onto un-leveled gear with no limit. The cap now applies from level 0 up, and only affects enchanted books (anvil repairs and renames are untouched).

## 1.0.1

### Changed
- Buffed Stealth trait.
- Removed Consuming Speed trait from the tool pool.

### Fixed
- Fixed tools using the weapon trait pool.

## 1.0.0

Initial release.
