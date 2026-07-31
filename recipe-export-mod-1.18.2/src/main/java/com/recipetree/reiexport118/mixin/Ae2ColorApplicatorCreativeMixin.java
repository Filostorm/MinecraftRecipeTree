package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2CreativeExemplarRepair;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Canonicalizes AE2's unordered full color-applicator creative exemplar. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.AE2_COLOR_APPLICATOR_CLASS, remap = false)
public abstract class Ae2ColorApplicatorCreativeMixin {
    @Unique
    private static final String REIEXPORT$CREATE_FULL =
            "createFullColorApplicator()Lnet/minecraft/world/item/ItemStack;";

    @Inject(method = REIEXPORT$CREATE_FULL, at = @At("RETURN"), require = 1, remap = false)
    private static void reiexport$canonicalizeFullColorApplicator(
            CallbackInfoReturnable<ItemStack> callbackInfo
    ) {
        Mm2CreativeExemplarRepair.canonicalizeAe2ColorApplicator(
                callbackInfo.getReturnValue());
    }
}
