package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2PotionBucketOrderCompatibility;
import net.minecraft.world.item.alchemy.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.List;

/** Makes equal translated potion names deterministic before REI selects representatives. */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.items.PotionBucketItem", remap = false)
public abstract class ImmersiveEngineeringPotionBucketOrderMixin {
    private static final String FILL_ITEM_CATEGORY =
            "m_6787_(Lnet/minecraft/world/item/CreativeModeTab;"
                    + "Lnet/minecraft/core/NonNullList;)V";

    @Redirect(
            method = FILL_ITEM_CATEGORY,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;sort(Ljava/util/Comparator;)V"),
            require = 1,
            remap = false)
    private void reiexport$canonicalPotionBucketOrder(
            List<Potion> potions,
            Comparator<Potion> upstreamComparator
    ) {
        Mm2PotionBucketOrderCompatibility.sort(potions, upstreamComparator);
    }
}
