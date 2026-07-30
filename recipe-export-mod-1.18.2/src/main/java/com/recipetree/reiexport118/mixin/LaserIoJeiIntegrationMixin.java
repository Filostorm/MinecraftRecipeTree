package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.LaserIoJeiRuntimeCompatibility;
import mezz.jei.api.runtime.IJeiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.direwolf20.laserio.client.jei.JEIIntegration", remap = false)
public abstract class LaserIoJeiIntegrationMixin {
    @Inject(
            method = "onRuntimeAvailable(Lmezz/jei/api/runtime/IJeiRuntime;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void reiexport$preserveExactRecipeHideContract(
            IJeiRuntime jeiRuntime,
            CallbackInfo callback
    ) {
        if (LaserIoJeiRuntimeCompatibility.hideExactResetRecipeCorpus(jeiRuntime)) {
            callback.cancel();
        }
    }
}
