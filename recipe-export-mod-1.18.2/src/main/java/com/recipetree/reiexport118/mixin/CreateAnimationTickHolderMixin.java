package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2PreviewRenderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Freezes Create's recipe-model phase only during an exact MM2 native preview capture. */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.utility.AnimationTickHolder", remap = false)
public abstract class CreateAnimationTickHolderMixin {
    @Inject(
            method = "getRenderTime()F",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void reiexport$canonicalRecipeRenderTime(
            CallbackInfoReturnable<Float> callback
    ) {
        if (!Mm2PreviewRenderClock.isCaptureActive()) {
            return;
        }
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.CREATE.modId());
        callback.setReturnValue(Mm2PreviewRenderClock.createRenderTime());
    }
}
