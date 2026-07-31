package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bypasses ImmediateWorldSceneRenderer's physical-window remap for exporter capture pixels. */
@Pseudo
@Mixin(targets = LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS, remap = false)
public abstract class LowDragImmediateRectMixin {
    @Inject(
            method = "getPositionedRect(IIII)Lcom/lowdragmc/lowdraglib/utils/PositionedRect;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private void reiexport$useCaptureSpacePositionedRect(
            int x,
            int y,
            int width,
            int height,
            CallbackInfoReturnable<Object> callbackInfo
    ) {
        Object replacement = LowDragFboViewportCompatibility.overrideImmediatePositionedRect(
                getClass().getName(), this, x, y, width, height);
        if (replacement != null) {
            callbackInfo.setReturnValue(replacement);
        }
    }
}
