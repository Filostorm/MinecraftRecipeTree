package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2PreviewRenderClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freezes LowDrag's native progress arrows only during an exact MM2 preview capture. */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.gui.widget.ProgressWidget", remap = false)
public abstract class LowDragProgressWidgetMixin {
    @Redirect(
            method = "lambda$static$0()D",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/System;currentTimeMillis()J"),
            require = 1,
            remap = false)
    private static long reiexport$canonicalRecipeWallMillis() {
        if (!Mm2PreviewRenderClock.isCaptureActive()) {
            return System.currentTimeMillis();
        }
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.LOW_DRAG_LIB.modId());
        return Mm2PreviewRenderClock.wallMillis(
                Mm2PreviewRenderClock.Source.LOW_DRAG_PROGRESS);
    }
}
