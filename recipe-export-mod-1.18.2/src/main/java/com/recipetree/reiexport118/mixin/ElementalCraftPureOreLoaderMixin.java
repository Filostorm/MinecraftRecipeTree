package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.util.Lazy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.stream.Stream;

/** Sorts ElementalCraft source-tag holders before its order-sensitive grouping pass. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.ELEMENTAL_PURE_ORE_LOADER_CLASS, remap = false)
public abstract class ElementalCraftPureOreLoaderMixin {
    @Shadow(remap = false)
    private Lazy<HolderSet.Named<Item>> source;

    @Shadow(remap = false)
    private TagKey<Item> sourceTag;

    @Inject(
            method = "streamSourceTag()Ljava/util/stream/Stream;",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void reiexport$streamStableSourceDomain(
            CallbackInfoReturnable<Stream<Holder<Item>>> callback
    ) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.ELEMENTAL_CRAFT.modId());
        HolderSet.Named<Item> holders = source.get();
        if (holders == null) {
            throw new IllegalStateException(
                    "ElementalCraft pure-ore source tag resolved null: " + sourceTag.location());
        }
        callback.setReturnValue(holders.stream()
                .sorted(Comparator.comparing(holder -> {
                    Item value = holder.value();
                    if (Registry.ITEM.getKey(value) == null) {
                        throw new IllegalStateException(
                                "ElementalCraft pure-ore source contains an unregistered item");
                    }
                    return Registry.ITEM.getKey(value).toString();
                })));
    }
}
