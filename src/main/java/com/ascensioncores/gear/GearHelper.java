package com.ascensioncores.gear;

import com.ascensioncores.AscensionCoresConfig;
import com.ascensioncores.compat.ArtifactsCompat;
import com.ascensioncores.compat.FarmersDelightCompat;
import com.ascensioncores.compat.MoreDelightCompat;
import com.ascensioncores.compat.ProgressionRebornCompat;
import com.ascensioncores.component.ModComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class GearHelper {

    // ── Classification ──────────────────────────────────────────────────────

    public static boolean isWeapon(ItemStack stack) {
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            return false;
        }
        Item item = stack.getItem();
        return stack.has(DataComponents.WEAPON)
            || item == Items.TRIDENT
            || item == Items.MACE
            || item == Items.BOW
            || item == Items.CROSSBOW
            || isNamedRangedWeapon(stack);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT
            || isNamedRangedWeapon(stack);
    }

    public static boolean isArmor(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot slot = eq.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    /** Tools = has TOOL component but not WEAPON (pickaxes, shovels, hoes). Axes are weapons. */
    public static boolean isTool(ItemStack stack) {
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
            return true;
        }
        return stack.has(DataComponents.TOOL) && !isWeapon(stack);
    }

    public static boolean isGear(ItemStack stack) {
        return isWeapon(stack) || isArmor(stack) || isTool(stack) || ArtifactsCompat.isArtifact(stack.getItem());
    }

    // ── Component helpers ───────────────────────────────────────────────────

    public static boolean hasAscensionData(ItemStack stack) {
        return stack.has(ModComponents.ASCENSION_LEVEL);
    }

    public static int getLevel(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ASCENSION_LEVEL, 0);
    }

    public static List<RolledStat> getRolledStats(ItemStack stack) {
        List<RolledStat> stats = stack.get(ModComponents.ROLLED_STATS);
        return stats != null ? stats : List.of();
    }

    // ── Upgrade operations ──────────────────────────────────────────────────

    public static void levelUp(ItemStack stack) {
        setLevel(stack, getLevel(stack) + 1, null);
    }

    public static void levelUpDeterministic(ItemStack stack) {
        setLevel(stack, getLevel(stack) + 1, new Random(getUpgradeSeed(stack)));
    }

    public static void fillTraitsDeterministic(ItemStack stack) {
        setLevel(stack, getLevel(stack), new Random(getUpgradeSeed(stack)));
    }

    public static void setLevel(ItemStack stack, int requestedLevel) {
        setLevel(stack, requestedLevel, null);
    }

    private static void setLevel(ItemStack stack, int requestedLevel, Random rng) {
        int newLevel = Math.max(0, Math.min(requestedLevel, getMaxLevel()));
        List<RolledStat> stats = new ArrayList<>(getRolledStats(stack));

        int statCount = Math.min(newLevel, getMaterialCapacity(stack));
        while (stats.size() > statCount) {
            stats.remove(stats.size() - 1);
        }
        while (stats.size() < statCount) {
            RolledStat rolled = rng != null
                ? StatPool.rollStat(stats, getPool(stack), newLevel, rng)
                : StatPool.rollStat(stats, getPool(stack), newLevel);
            if (rolled == null) break;
            stats.add(rolled);
        }

        stack.set(ModComponents.ASCENSION_LEVEL, newLevel);
        stack.set(ModComponents.ROLLED_STATS, stats);

        rebuildAttributes(stack, newLevel, stats);
    }

    public static void rerollDeterministic(ItemStack stack, int rerollCount) {
        reroll(stack, new Random(getChaosRerollSeed(stack)), rerollCount);
    }

    private static void reroll(ItemStack stack, Random random, int rerollCount) {
        int level = getLevel(stack);
        int statCount = Math.min(level, getMaterialCapacity(stack));
        List<StatPool.StatDef> pool = getPool(stack);

        List<RolledStat> existing = getRolledStats(stack);
        int keep = Math.max(0, existing.size() - rerollCount);
        List<RolledStat> stats = new ArrayList<>(existing.subList(0, keep));
        while (stats.size() < statCount) {
            RolledStat s = StatPool.rollStat(stats, pool, level, random, AscensionCoresConfig.chaosGambleMode);
            if (s != null) stats.add(s);
            else break;
        }
        stack.set(ModComponents.ROLLED_STATS, stats);
        rebuildAttributes(stack, level, stats);
    }

    public static long getUpgradeSeed(ItemStack stack) {
        long seed = 0xDEADBEEFCAFEL;
        if (stack.has(ModComponents.RANDOM_SEED)) {
            seed = mixSeed(seed, stack.get(ModComponents.RANDOM_SEED));
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        seed = mixSeed(seed, itemId.toString().hashCode());
        seed = mixSeed(seed, getLevel(stack));
        seed = mixSeed(seed, getMaterialCapacity(stack));
        for (RolledStat stat : getRolledStats(stack)) {
            seed = mixSeed(seed, stat.id().hashCode());
            seed = mixSeed(seed, Double.doubleToLongBits(stat.amount()));
        }
        return seed;
    }

    private static long getChaosRerollSeed(ItemStack stack) {
        long seed = 0x5DEECE66DL;
        if (stack.has(ModComponents.RANDOM_SEED)) {
            seed = mixSeed(seed, stack.get(ModComponents.RANDOM_SEED));
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        seed = mixSeed(seed, itemId.toString().hashCode());
        seed = mixSeed(seed, getLevel(stack));
        seed = mixSeed(seed, getMaterialCapacity(stack));

        for (RolledStat stat : getRolledStats(stack)) {
            seed = mixSeed(seed, stat.id().hashCode());
            seed = mixSeed(seed, Double.doubleToLongBits(stat.amount()));
        }
        return seed;
    }

    private static long mixSeed(long seed, long value) {
        long mixed = seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
        return mixed ^ (mixed >>> 32);
    }

    /** Counts enchantments on the stack, excluding curses (Binding, Vanishing). */
    public static int countNonCurseEnchantments(ItemStack stack) {
        ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int count = 0;
        for (var entry : enchants.entrySet()) {
            if (!entry.getKey().is(EnchantmentTags.CURSE)) count++;
        }
        return count;
    }

    public static int getMaxLevel() {
        return AscensionCoresConfig.maxLevel;
    }

    public static int getAscensionCoreCost(int currentLevel) {
        return AscensionCoresConfig.getUpgradeCoreCost(currentLevel);
    }

    /** Total Ascension Cores spent to bring gear from level 0 up to {@code level}. */
    public static int getTotalCoreCost(int level) {
        int total = 0;
        for (int i = 0; i < level; i++) {
            total += getAscensionCoreCost(i);
        }
        return total;
    }

    // ── Attribute building ──────────────────────────────────────────────────

    public static void rebuildAttributes(ItemStack stack, int level, List<RolledStat> stats) {
        EquipmentSlotGroup slotGroup = isArmor(stack) ? getArmorSlotGroup(stack) : EquipmentSlotGroup.MAINHAND;

        ItemAttributeModifiers vanilla = stack.getPrototype().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers current = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        Set<ModifierKey> addedModifiers = new HashSet<>();
        // The stack component is authoritative: other mods may intentionally
        // replace a prototype modifier while keeping its attribute and ID.
        addNonAscensionModifiers(builder, current, addedModifiers);
        addNonAscensionModifiers(builder, vanilla, addedModifiers);

        for (int i = 0; i < stats.size(); i++) {
            RolledStat rolled = stats.get(i);
            StatPool.StatDef def = StatPool.getById(rolled.id());
            if (def == null || def.attribute() == null) continue;
            double amount = rolled.amount() * (level - i);
            AttributeModifier mod = new AttributeModifier(
                Identifier.fromNamespaceAndPath("ascensioncores", "stat_" + def.id() + "_" + i),
                amount,
                def.operation()
            );
            builder.add(def.attribute(), mod, slotGroup, ItemAttributeModifiers.Display.hidden());
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());

        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        stack.set(DataComponents.TOOLTIP_DISPLAY,
            display.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, false));
    }

    /**
     * Auto-repair the rolled-stat list: drops trait IDs no longer in any pool (e.g.,
     * traits removed in a mod update), then refills any empty slots up to the item's
     * unlocked trait count. Intended for migration-time scans, such as when a
     * player inventory is loaded after a mod update.
     */
    public static boolean repairTraitGaps(ItemStack stack) {
        if (!isGear(stack) || !hasAscensionData(stack)) return false;
        int level = getLevel(stack);
        if (level <= 0) return false;

        List<RolledStat> stats = new ArrayList<>(getRolledStats(stack));
        boolean changed = stats.removeIf(s -> StatPool.getById(s.id()) == null);

        int expected = Math.min(level, getMaterialCapacity(stack));
        while (stats.size() < expected) {
            RolledStat rolled = StatPool.rollStat(stats, getPool(stack), level);
            if (rolled == null) break;
            stats.add(rolled);
            changed = true;
        }

        if (changed) {
            stack.set(ModComponents.ROLLED_STATS, stats);
            rebuildAttributes(stack, level, stats);
        }
        return changed;
    }

    public static void rebuildAttributesIfOutdated(ItemStack stack) {
        int level = getLevel(stack);
        if (level <= 0) return;

        ItemAttributeModifiers prototype = stack.getPrototype().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers current = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Set<ModifierKey> currentKeys = new HashSet<>();
        collectNonAscensionModifierKeys(current, currentKeys);
        for (ItemAttributeModifiers.Entry entry : prototype.modifiers()) {
            Identifier id = entry.modifier().id();
            if ("ascensioncores".equals(id.getNamespace())) continue;
            if (!currentKeys.contains(new ModifierKey(entry.attribute(), id, entry.slot()))) {
                rebuildAttributes(stack, level, getRolledStats(stack));
                return;
            }
        }

        List<RolledStat> stats = getRolledStats(stack);
        for (int i = 0; i < stats.size(); i++) {
            RolledStat rolled = stats.get(i);
            StatPool.StatDef def = StatPool.getById(rolled.id());
            if (def == null || def.attribute() == null) continue;

            Identifier expectedId = Identifier.fromNamespaceAndPath("ascensioncores", "stat_" + def.id() + "_" + i);
            if (!hasAscensionModifier(stack, expectedId, def.operation())) {
                rebuildAttributes(stack, level, stats);
                return;
            }
        }
    }

    private static boolean hasAscensionModifier(ItemStack stack, Identifier id, AttributeModifier.Operation operation) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            AttributeModifier modifier = entry.modifier();
            if (modifier.id().equals(id) && modifier.operation() == operation) {
                return true;
            }
        }
        return false;
    }

    private static void addNonAscensionModifiers(
            ItemAttributeModifiers.Builder builder,
            ItemAttributeModifiers modifiers,
            Set<ModifierKey> addedModifiers) {
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            Identifier id = entry.modifier().id();
            ModifierKey key = new ModifierKey(entry.attribute(), id, entry.slot());
            if ("ascensioncores".equals(id.getNamespace()) || !addedModifiers.add(key)) {
                continue;
            }
            builder.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
        }
    }

    private static void collectNonAscensionModifierKeys(
            ItemAttributeModifiers modifiers,
            Set<ModifierKey> keys) {
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            Identifier id = entry.modifier().id();
            if (!"ascensioncores".equals(id.getNamespace())) {
                keys.add(new ModifierKey(entry.attribute(), id, entry.slot()));
            }
        }
    }

    private record ModifierKey(
            Holder<Attribute> attribute,
            Identifier id,
            EquipmentSlotGroup slot) {}

    // ── Material capacity ───────────────────────────────────────────────────

    public static int getMaterialCapacity(ItemStack stack) {
        Item item = stack.getItem();
        if (isTier0(item)) return 1;
        if (isTier1(item)) return 2;
        if (isTier2(item)) return 3;
        if (isTier3(item)) return 4;
        if (isTier4(item)) return 5;
        int artifactsCapacity = ArtifactsCompat.getMaterialCapacity(item);
        if (artifactsCapacity > 0) return artifactsCapacity;
        int farmersDelightCapacity = FarmersDelightCompat.getMaterialCapacity(item);
        if (farmersDelightCapacity > 0) return farmersDelightCapacity;
        int moreDelightCapacity = MoreDelightCompat.getMaterialCapacity(item);
        if (moreDelightCapacity > 0) return moreDelightCapacity;
        int progressionRebornCapacity = ProgressionRebornCompat.getMaterialCapacity(item);
        if (progressionRebornCapacity > 0) return progressionRebornCapacity;
        int namedMaterialCapacity = getNamedMaterialCapacity(stack);
        if (namedMaterialCapacity >= 0) return namedMaterialCapacity;
        return inferMaterialCapacity(stack);
    }

    private static boolean isTier0(Item item) {
        return item == Items.WOODEN_SWORD   || item == Items.WOODEN_AXE   || item == Items.WOODEN_SPEAR
            || item == Items.WOODEN_PICKAXE || item == Items.WOODEN_SHOVEL || item == Items.WOODEN_HOE
            || item == Items.GOLDEN_SWORD   || item == Items.GOLDEN_AXE   || item == Items.GOLDEN_SPEAR
            || item == Items.GOLDEN_PICKAXE || item == Items.GOLDEN_SHOVEL || item == Items.GOLDEN_HOE
            || item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE
            || item == Items.LEATHER_LEGGINGS || item == Items.LEATHER_BOOTS;
    }

    private static boolean isTier1(Item item) {
        return item == Items.STONE_SWORD    || item == Items.STONE_AXE   || item == Items.STONE_SPEAR
            || item == Items.STONE_PICKAXE  || item == Items.STONE_SHOVEL || item == Items.STONE_HOE
            || item == Items.COPPER_SWORD   || item == Items.COPPER_AXE  || item == Items.COPPER_SPEAR
            || item == Items.COPPER_PICKAXE || item == Items.COPPER_SHOVEL || item == Items.COPPER_HOE
            || item == Items.COPPER_HELMET  || item == Items.COPPER_CHESTPLATE
            || item == Items.COPPER_LEGGINGS || item == Items.COPPER_BOOTS
            || item == Items.CHAINMAIL_HELMET   || item == Items.CHAINMAIL_CHESTPLATE
            || item == Items.CHAINMAIL_LEGGINGS || item == Items.CHAINMAIL_BOOTS
            || item == Items.BOW;
    }

    private static boolean isTier2(Item item) {
        return item == Items.IRON_SWORD    || item == Items.IRON_AXE   || item == Items.IRON_SPEAR
            || item == Items.IRON_PICKAXE  || item == Items.IRON_SHOVEL || item == Items.IRON_HOE
            || item == Items.IRON_HELMET   || item == Items.IRON_CHESTPLATE
            || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS
            || item == Items.CROSSBOW;
    }

    private static boolean isTier3(Item item) {
        return item == Items.DIAMOND_SWORD    || item == Items.DIAMOND_AXE   || item == Items.DIAMOND_SPEAR
            || item == Items.DIAMOND_PICKAXE  || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE
            || item == Items.DIAMOND_HELMET   || item == Items.DIAMOND_CHESTPLATE
            || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS
            || item == Items.TURTLE_HELMET
            || item == Items.TRIDENT;
    }

    private static boolean isTier4(Item item) {
        return item == Items.NETHERITE_SWORD    || item == Items.NETHERITE_AXE   || item == Items.NETHERITE_SPEAR
            || item == Items.NETHERITE_PICKAXE  || item == Items.NETHERITE_SHOVEL || item == Items.NETHERITE_HOE
            || item == Items.NETHERITE_HELMET   || item == Items.NETHERITE_CHESTPLATE
            || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS
            || item == Items.MACE;
    }

    private static int getNamedMaterialCapacity(ItemStack stack) {
        if (!isGear(stack)) return -1;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return -1;

        String[] tokens = id.getPath().toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        if (hasToken(tokens, "netherite")) return 5;
        if (hasToken(tokens, "diamond")) return 4;
        if (hasToken(tokens, "iron", "steel", "rose")) return 3;
        if (hasToken(tokens, "stone", "copper", "chainmail", "chain", "bronze")) return 2;
        if (hasToken(tokens, "wood", "wooden", "gold", "golden", "leather", "flint")) return 1;
        return -1;
    }

    private static boolean hasToken(String[] tokens, String... candidates) {
        for (String token : tokens) {
            for (String candidate : candidates) {
                if (token.equals(candidate)) return true;
            }
        }
        return false;
    }

    private static boolean isNamedRangedWeapon(ItemStack stack) {
        if (!stack.isDamageableItem()) return false;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;

        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.equals("bow")
            || path.equals("crossbow")
            || path.endsWith("_bow")
            || path.endsWith("_crossbow")
            || path.endsWith("longbow")
            || path.endsWith("shortbow");
    }

    private static int inferMaterialCapacity(ItemStack stack) {
        if (!isGear(stack)) return 0;
        return Math.max(inferCapacityFromDurability(stack), inferCapacityFromBaseAttributes(stack));
    }

    private static int inferCapacityFromDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) return 0;

        int maxDamage = stack.getMaxDamage();
        if (maxDamage >= 1900) return 5;
        if (maxDamage >= 1000) return 4;
        if (maxDamage >= 240) return 3;
        if (maxDamage >= 100) return 2;
        return 1;
    }

    private static int inferCapacityFromBaseAttributes(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getPrototype().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (isArmor(stack)) {
            EquipmentSlot slot = getArmorSlot(stack);
            if (slot == null) return 0;

            double toughness = modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0.0, slot);
            if (toughness >= 3.0) return 5;
            if (toughness >= 2.0) return 4;

            double armor = modifiers.compute(Attributes.ARMOR, 0.0, slot);
            return switch (slot) {
                case HEAD, FEET -> {
                    if (armor >= 3.0) yield 4;
                    if (armor >= 2.0) yield 3;
                    if (armor >= 1.0) yield 2;
                    yield 1;
                }
                case CHEST -> {
                    if (armor >= 8.0) yield 4;
                    if (armor >= 6.0) yield 3;
                    if (armor >= 5.0) yield 2;
                    yield 1;
                }
                case LEGS -> {
                    if (armor >= 6.0) yield 4;
                    if (armor >= 5.0) yield 3;
                    if (armor >= 4.0) yield 2;
                    yield 1;
                }
                default -> 1;
            };
        }

        if (isWeapon(stack)) {
            double attackDamage = modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
            if (attackDamage >= 8.0) return 5;
            if (attackDamage >= 6.0) return 4;
            if (attackDamage >= 5.0) return 3;
            if (attackDamage >= 4.0) return 2;
        }

        return 1;
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    public static List<StatPool.StatDef> getPool(ItemStack stack) {
        if (ArtifactsCompat.isHandArtifact(stack.getItem())) return StatPool.WEAPON_POOL;
        if (ArtifactsCompat.isArtifact(stack.getItem())) return StatPool.ARMOR_POOL;
        if (isTool(stack)) return StatPool.TOOL_POOL;
        if (isRangedWeapon(stack)) return StatPool.RANGED_WEAPON_POOL;
        if (isWeapon(stack)) return StatPool.WEAPON_POOL;
        if (isArmor(stack))  return StatPool.ARMOR_POOL;
        return StatPool.TOOL_POOL;
    }

    public static double getScaledStatAmount(ItemStack stack, String statId) {
        int level = getLevel(stack);
        double total = 0.0;
        List<RolledStat> stats = getRolledStats(stack);

        for (int i = 0; i < stats.size(); i++) {
            RolledStat rolled = stats.get(i);
            if (rolled.id().equals(statId)) {
                double amount = rolled.amount() * (level - i);
                total += amount;
            }
        }

        return total;
    }

    public static double getScaledArmorStatAmount(LivingEntity entity, String statId) {
        return getScaledStatAmount(entity.getItemBySlot(EquipmentSlot.HEAD), statId)
            + getScaledStatAmount(entity.getItemBySlot(EquipmentSlot.CHEST), statId)
            + getScaledStatAmount(entity.getItemBySlot(EquipmentSlot.LEGS), statId)
            + getScaledStatAmount(entity.getItemBySlot(EquipmentSlot.FEET), statId);
    }

    public static double getScaledEquippedArtifactStatAmount(LivingEntity entity, String statId) {
        final double[] total = {0.0};
        ArtifactsCompat.forEachEquippedArtifact(entity, stack -> total[0] += getScaledStatAmount(stack, statId));
        return total[0];
    }

    private static EquipmentSlotGroup getArmorSlotGroup(ItemStack stack) {
        EquipmentSlot slot = getArmorSlot(stack);
        if (slot == null) return EquipmentSlotGroup.ANY;
        return switch (slot) {
            case HEAD  -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS  -> EquipmentSlotGroup.LEGS;
            case FEET  -> EquipmentSlotGroup.FEET;
            default    -> EquipmentSlotGroup.ANY;
        };
    }

    private static EquipmentSlot getArmorSlot(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq == null ? null : eq.slot();
    }
}
