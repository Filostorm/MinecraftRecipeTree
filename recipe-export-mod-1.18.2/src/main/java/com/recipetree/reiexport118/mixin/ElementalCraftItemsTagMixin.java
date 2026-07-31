package com.recipetree.reiexport118.mixin;

import com.mojang.datafixers.util.Pair;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.function.Predicate;

/** Makes ElementalCraft's ambiguous Forge-tag selection lexicographically deterministic. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.ELEMENTAL_ITEMS_TAGS_CLASS, remap = false)
public abstract class ElementalCraftItemsTagMixin {
    @Inject(
            method = "getTag(Ljava/util/function/Predicate;)Lnet/minecraft/core/HolderSet$Named;",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void reiexport$chooseStableMatchingTag(
            Predicate<TagKey<Item>> predicate,
            CallbackInfoReturnable<HolderSet.Named<Item>> callback
    ) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.ELEMENTAL_CRAFT.modId());
        HolderSet.Named<Item> selected = Registry.ITEM.getTags()
                .filter(pair -> predicate.test(pair.getFirst()))
                .sorted(Comparator.comparing(
                        pair -> pair.getFirst().location().toString()))
                .map(Pair::getSecond)
                .findFirst()
                .orElse(null);
        callback.setReturnValue(selected);
    }
}
