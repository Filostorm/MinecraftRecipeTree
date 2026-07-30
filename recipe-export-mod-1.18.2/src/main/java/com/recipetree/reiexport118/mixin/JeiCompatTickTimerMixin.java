package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2PreviewRenderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freezes JEI-compatible progress widgets only during an exact MM2 native preview capture. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.jeicompat.wrap.JEIGuiHelper$6", remap = false)
public abstract class JeiCompatTickTimerMixin {
    @Redirect(
            method = "getValue()I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/System;currentTimeMillis()J"),
            require = 1,
            remap = false)
    private long reiexport$canonicalRecipeWallMillis() {
        if (!Mm2PreviewRenderClock.isCaptureActive()) {
            return System.currentTimeMillis();
        }
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.REI_PLUGIN_COMPAT.modId());
        return Mm2PreviewRenderClock.wallMillis(
                Mm2PreviewRenderClock.Source.JEI_COMPAT_TICK_TIMER);
    }
}
