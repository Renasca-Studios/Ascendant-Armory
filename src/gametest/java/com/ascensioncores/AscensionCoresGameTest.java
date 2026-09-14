package com.ascensioncores;

import com.ascensioncores.gear.GearHelper;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public final class AscensionCoresGameTest implements CustomTestMethodInvoker {

    @GameTest
    public void sameItemDurabilityRepairKeepsVanillaResult(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        player.experienceLevel = 30;
        AnvilMenu menu = new AnvilMenu(1, player.getInventory());

        ItemStack left = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemStack right = new ItemStack(Items.DIAMOND_CHESTPLATE);
        left.setDamageValue(left.getMaxDamage() - 10);
        right.setDamageValue(right.getMaxDamage() - 10);
        menu.getSlot(0).set(left);
        menu.getSlot(1).set(right);

        ItemStack result = menu.getSlot(2).getItem();
        helper.assertFalse(result.isEmpty(), "Same-item anvil repair produced no result");
        helper.assertTrue(result.getDamageValue() < left.getDamageValue(),
            "Same-item anvil result did not repair durability");
        helper.succeed();
    }

    @GameTest
    public void ascendingPreservesEveryPrototypeModifier(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.NETHERITE_CHESTPLATE);
        Set<ModifierKey> expected = modifierKeys(stack.getPrototype().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));

        GearHelper.setLevel(stack, 1);

        Set<ModifierKey> actual = modifierKeys(stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
        helper.assertTrue(actual.containsAll(expected),
            "Ascending removed one or more Netherite prototype modifiers");
        helper.succeed();
    }

    @GameTest
    public void stackModifierOverridesPrototypeDefault(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemAttributeModifiers original = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Entry target = original.modifiers().getFirst();
        double replacementAmount = target.modifier().amount() + 7.0;

        ItemAttributeModifiers.Builder customized = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : original.modifiers()) {
            if (entry == target) {
                AttributeModifier replacement = new AttributeModifier(
                    entry.modifier().id(), replacementAmount, entry.modifier().operation());
                customized.add(entry.attribute(), replacement, entry.slot(), entry.display());
            } else {
                customized.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, customized.build());

        GearHelper.setLevel(stack, 1);

        double actual = findAmount(stack, target.attribute(), target.modifier().id(), target.slot());
        helper.assertValueEqual(actual, replacementAmount,
            "Ascending replaced a stack-specific modifier with its prototype default");
        helper.succeed();
    }

    @GameTest
    public void migrationRestoresMissingPrototypeModifier(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.NETHERITE_CHESTPLATE);
        GearHelper.setLevel(stack, 1);
        ItemAttributeModifiers current = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Entry missing = stack.getPrototype().getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().getLast();

        ItemAttributeModifiers.Builder broken = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            if (!matches(entry, missing.attribute(), missing.modifier().id(), missing.slot())) {
                broken.add(entry.attribute(), entry.modifier(), entry.slot(), entry.display());
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, broken.build());

        GearHelper.rebuildAttributesIfOutdated(stack);

        helper.assertTrue(hasModifier(stack, missing.attribute(), missing.modifier().id(), missing.slot()),
            "Migration did not restore a missing prototype modifier");
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static Set<ModifierKey> modifierKeys(ItemAttributeModifiers modifiers) {
        Set<ModifierKey> keys = new HashSet<>();
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (!"ascensioncores".equals(entry.modifier().id().getNamespace())) {
                keys.add(new ModifierKey(entry.attribute(), entry.modifier().id(), entry.slot()));
            }
        }
        return keys;
    }

    private static boolean hasModifier(ItemStack stack, Holder<Attribute> attribute,
            Identifier id, EquipmentSlotGroup slot) {
        return !Double.isNaN(findAmount(stack, attribute, id, slot));
    }

    private static double findAmount(ItemStack stack, Holder<Attribute> attribute,
            Identifier id, EquipmentSlotGroup slot) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (matches(entry, attribute, id, slot)) return entry.modifier().amount();
        }
        return Double.NaN;
    }

    private static boolean matches(ItemAttributeModifiers.Entry entry, Holder<Attribute> attribute,
            Identifier id, EquipmentSlotGroup slot) {
        return entry.attribute().equals(attribute)
            && entry.modifier().id().equals(id)
            && entry.slot().equals(slot);
    }

    private record ModifierKey(Holder<Attribute> attribute, Identifier id, EquipmentSlotGroup slot) {}
}
