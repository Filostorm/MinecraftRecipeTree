package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restores the exporter viewport at LowDrag's exact resetCamera transition. */
@Pseudo
@Mixin(targets = LowDragFboViewportContract.WORLD_RENDERER_CLASS, remap = false)
public abstract class LowDragImmediateViewportMixin {
    @Inject(
            method = "drawWorld()V",
            at = @At("HEAD"),
            require = 1,
            remap = false
    )
    private void reiexport$auditImmediateWorldDrawEntry(CallbackInfo callbackInfo) {
        LowDragFboViewportCompatibility.beforeImmediateWorldDraw(
                getClass().getName(), this);
    }

    @Inject(
            method = "drawWorld()V",
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void reiexport$auditImmediateWorldDrawReturn(CallbackInfo callbackInfo) {
        LowDragFboViewportCompatibility.afterImmediateWorldDraw(
                getClass().getName(), this);
    }

    @Inject(
            method = "resetCamera()V",
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void reiexport$restoreExporterViewportAfterImmediateReset(CallbackInfo callbackInfo) {
        LowDragFboViewportCompatibility.restoreExporterViewportAfterImmediateReset(
                getClass().getName()
        );
    }
}
