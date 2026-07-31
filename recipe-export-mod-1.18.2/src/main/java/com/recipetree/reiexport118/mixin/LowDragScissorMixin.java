package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces LowDrag's Minecraft-window scissor mapping only inside exporter-owned captures. */
@Pseudo
@Mixin(targets = LowDragFboViewportContract.RENDER_UTILS_CLASS, remap = false)
public abstract class LowDragScissorMixin {
    @Inject(
            method = "peekFirstScissorOrFullScreen()[I",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void reiexport$replaceWindowScissorBounds(
            CallbackInfoReturnable<int[]> callbackInfo
    ) {
        int[] replacement = LowDragFboViewportCompatibility.replaceWindowScissorBounds(
                callbackInfo.getReturnValue());
        if (replacement != null) {
            callbackInfo.setReturnValue(replacement);
        }
    }

    @Inject(
            method = "applyScissor(IIII)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false
    )
    private static void reiexport$applyCaptureSpaceScissor(
            int x,
            int y,
            int width,
            int height,
            CallbackInfo callbackInfo
    ) {
        if (LowDragFboViewportCompatibility.applyExporterScissor(
                x, y, width, height)) {
            callbackInfo.cancel();
        }
    }
}
