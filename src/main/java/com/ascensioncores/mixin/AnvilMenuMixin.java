package com.ascensioncores.mixin;

import com.ascensioncores.AscensionCoresConfig;
import com.ascensioncores.component.ModComponents;
import com.ascensioncores.gear.GearHelper;
import com.ascensioncores.gear.RolledStat;
import com.ascensioncores.item.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow private int repairItemCountCost;
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void interceptCreateResult(CallbackInfo ci) {
        ItemCombinerMenuAccessor acc = (ItemCombinerMenuAccessor) (Object) this;
        Container inputSlots = acc.getInputSlots();
        ResultContainer resultSlots = acc.getResultSlots();

        ItemStack left  = inputSlots.getItem(0);
        ItemStack right = inputSlots.getItem(1);

        if (left.isEmpty()) return;
        if (!GearHelper.isGear(left)) return;

        // ── Salvage: leveled gear alone — no second item, no rename ────────
        if (right.isEmpty()) {
            if (AscensionCoresConfig.enableSalvage
                    && GearHelper.getLevel(left) > 0
                    && resultSlots.getItem(0).isEmpty()) {   // skip if a rename is pending
                int total = GearHelper.getTotalCoreCost(GearHelper.getLevel(left));
                int refund = Math.min(64, (int) Math.floor(total * AscensionCoresConfig.salvageRefundPercent));
                if (refund > 0) {
                    resultSlots.setItem(0, new ItemStack(ModItems.ASCENSION_CORE, refund));
                    repairItemCountCost = 0;
                    cost.set(Math.max(1, GearHelper.getLevel(left)));
                }
            }
            return;
        }

        Player seedPlayer = acc.getPlayer();
        if (seedPlayer != null && !seedPlayer.level().isClientSide()
                && !left.has(ModComponents.RANDOM_SEED)) {
            left.set(ModComponents.RANDOM_SEED, java.util.concurrent.ThreadLocalRandom.current().nextLong());
        }

        int currentLevel = GearHelper.getLevel(left);

        // ── A: Upgrade with cores ──────────────────────────────────────────
        if (right.is(ModItems.ASCENSION_CORE)) {
            int maxLevel = GearHelper.getMaxLevel();
            int capacity = Math.min(GearHelper.getMaterialCapacity(left), maxLevel);
            List<RolledStat> currentStats = GearHelper.getRolledStats(left);

            if (currentLevel >= maxLevel) {
                // At max level: fill empty trait slots if capacity expanded (e.g. material upgrade)
                if (currentStats.size() < capacity) {
                    ItemStack result = left.copy();
                    GearHelper.fillTraitsDeterministic(result);
                    resultSlots.setItem(0, result);
                    repairItemCountCost = 1;
                    cost.set(currentLevel);
                } else {
                    resultSlots.setItem(0, ItemStack.EMPTY);
                }
                return;
            }
            int coreCost = GearHelper.getAscensionCoreCost(currentLevel);
            if (right.getCount() < coreCost) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                return;
            }
            ItemStack result = left.copy();
            GearHelper.levelUpDeterministic(result);

            resultSlots.setItem(0, result);
            repairItemCountCost = coreCost;
            cost.set(ascensioncores$upgradeXpCost(currentLevel));
            return;
        }

        // ── B: Reroll with chaos core ──────────────────────────────────────
        if (right.is(ModItems.CHAOS_CORE)) {
            if (currentLevel == 0) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                Player player = ((ItemCombinerMenuAccessor) (Object) this).getPlayer();
                if (player != null && AscensionCoresConfig.playAnvilFeedback) {
                    player.sendOverlayMessage(
                        Component.literal("Chaos cores need leveled gear (L1+).")
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }
                return;
            }
            int currentTraits = GearHelper.getRolledStats(left).size();
            int rerollCount = Math.min(right.getCount(), currentTraits);
            if (rerollCount <= 0) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                return;
            }
            ItemStack result = left.copy();
            GearHelper.rerollDeterministic(result, rerollCount);

            resultSlots.setItem(0, result);
            repairItemCountCost = rerollCount;
            cost.set(ascensioncores$rerollXpCost(currentLevel) * rerollCount);
            return;
        }

        // ── C: Trait donation — same item type, both leveled ascension gear ──────────
        // Vanilla uses the same input shape for durability repair and enchantment
        // merging. Only treat it as donation when vanilla found no valid result.
        if (resultSlots.getItem(0).isEmpty()
                && GearHelper.isGear(right) && left.getItem() == right.getItem()) {
            int rightLevel = GearHelper.getLevel(right);
            if (currentLevel == 0 || rightLevel == 0) {
                resultSlots.setItem(0, ItemStack.EMPTY);
                return;
            }

            List<RolledStat> leftStats = GearHelper.getRolledStats(left);
            List<RolledStat> rightStats = GearHelper.getRolledStats(right);
            int capacity = Math.min(GearHelper.getMaterialCapacity(left), GearHelper.getMaxLevel());

            if (!rightStats.isEmpty() && leftStats.size() < capacity) {
                Set<String> leftIds = leftStats.stream()
                    .map(RolledStat::id).collect(Collectors.toSet());
                RolledStat donated = rightStats.stream()
                    .filter(s -> !leftIds.contains(s.id()))
                    .findFirst().orElse(null);

                if (donated != null) {
                    ItemStack result = left.copy();
                    List<RolledStat> newStats = new ArrayList<>(leftStats);
                    newStats.add(donated);
                    result.set(ModComponents.ROLLED_STATS, newStats);
                    GearHelper.rebuildAttributes(result, GearHelper.getLevel(result), newStats);

                    resultSlots.setItem(0, result);
                    repairItemCountCost = right.getCount();
                    cost.set(Math.max(1, rightLevel));
                    return;
                }
            }
            resultSlots.setItem(0, ItemStack.EMPTY);
            return;
        }

        // ── D: Enchanted book — enforce enchantment capacity ──────────────
        if (!AscensionCoresConfig.enableEnchantmentSlots) return;
        if (!right.has(DataComponents.STORED_ENCHANTMENTS)) return; // books only — skip repairs/renames
        ItemStack currentResult = resultSlots.getItem(0);
        if (currentResult.isEmpty()) return;

        int maxEnchants = currentLevel; // getLevel() → 0 for un-ascended gear, blocking all books
        if (GearHelper.countNonCurseEnchantments(currentResult) > maxEnchants) {
            resultSlots.setItem(0, ItemStack.EMPTY);
        }
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void applyAscensionAnvilResult(Player player, ItemStack stack, CallbackInfo ci) {
        ItemCombinerMenuAccessor acc = (ItemCombinerMenuAccessor) (Object) this;
        Container inputSlots = acc.getInputSlots();

        ItemStack left = inputSlots.getItem(0);
        ItemStack right = inputSlots.getItem(1);

        if (stack.isEmpty() || left.isEmpty()) return;

        // Salvage: leveled gear alone, result is a stack of Ascension Cores
        if (right.isEmpty()) {
            if (AscensionCoresConfig.enableSalvage && GearHelper.isGear(left)
                    && GearHelper.getLevel(left) > 0 && stack.is(ModItems.ASCENSION_CORE)) {
                playFeedback(player, "Salvaged: " + stack.getCount() + " Cores", 2);
            }
            return;
        }

        if (right.is(ModItems.ASCENSION_CORE) && GearHelper.isGear(left)) {
            playFeedback(player, "Level " + GearHelper.getLevel(stack), GearHelper.getLevel(stack));
            return;
        }

        if (right.is(ModItems.CHAOS_CORE)) {
            if (!GearHelper.isGear(left) || GearHelper.getLevel(left) == 0) return;
            playFeedback(player, "Stats rerolled", GearHelper.getLevel(stack));
        }
    }




    @org.spongepowered.asm.mixin.Unique
    private static int ascensioncores$upgradeXpCost(int currentLevel) {
        return AscensionCoresConfig.getUpgradeXpCost(currentLevel);
    }

    @org.spongepowered.asm.mixin.Unique
    private static int ascensioncores$rerollXpCost(int currentLevel) {
        return currentLevel;
    }

    private static void playFeedback(Player player, String message, int level) {
        if (!AscensionCoresConfig.playAnvilFeedback) return;
        net.minecraft.sounds.SoundEvent sound = switch (level) {
            case 1 -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case 2 -> SoundEvents.ENCHANTMENT_TABLE_USE;
            case 3 -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
            case 4 -> SoundEvents.ENDER_DRAGON_GROWL;
            default -> SoundEvents.ENCHANTMENT_TABLE_USE;
        };
        float pitch = switch (level) {
            case 1 -> 1.4f;
            case 2 -> 1.2f;
            case 3 -> 1.0f;
            case 4 -> 1.5f;
            default -> 1.2f;
        };
        float vol = level == 4 ? 0.4f : 0.6f;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch);
        player.sendOverlayMessage(Component.literal(message));
    }
}
