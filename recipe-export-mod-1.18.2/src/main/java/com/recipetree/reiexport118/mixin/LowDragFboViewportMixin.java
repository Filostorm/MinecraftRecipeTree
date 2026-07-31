package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = LowDragFboViewportContract.FBO_RENDERER_CLASS, remap = false)
public abstract class LowDragFboViewportMixin {
    @Inject(
            method = "unbindFBO(I)V",
            at = @At("RETURN"),
            require = 1,
            remap = false
    )
    private void reiexport$restoreExporterViewportBeforeComposite(
            int savedFramebuffer,
            CallbackInfo callbackInfo
    ) {
        LowDragFboViewportCompatibility.restoreExporterViewportAfterNestedFbo(savedFramebuffer);
    }
}
