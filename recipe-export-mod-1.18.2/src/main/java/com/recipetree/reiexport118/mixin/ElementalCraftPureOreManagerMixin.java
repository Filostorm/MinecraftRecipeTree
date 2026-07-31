package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.ElementalCraftPureOreDeterminism;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/** Stabilizes injector order and verifies the complete generated pure-ore domain. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.ELEMENTAL_PURE_ORE_MANAGER_CLASS, remap = false)
public abstract class ElementalCraftPureOreManagerMixin {
    @Inject(
            method = "getInjectors()Ljava/util/Collection;",
            at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private static void reiexport$sortInjectors(
            CallbackInfoReturnable<Collection<?>> callback
    ) {
        callback.setReturnValue(ElementalCraftPureOreDeterminism.sortInjectors(
                callback.getReturnValue()));
    }

    @Inject(
            method = "reload(Lsirttas/dpanvil/api/event/DataPackReloadCompleteEvent;)V",
            at = @At("RETURN"), require = 1, remap = false)
    private void reiexport$verifyGeneratedDomain(CallbackInfo callbackInfo) {
        ElementalCraftPureOreDeterminism.verifyManager(this);
    }
}
