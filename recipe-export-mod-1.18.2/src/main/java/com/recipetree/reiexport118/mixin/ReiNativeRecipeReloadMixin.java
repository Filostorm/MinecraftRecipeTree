package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import net.minecraft.world.item.crafting.RecipeManager;
import org.apache.commons.lang3.mutable.MutableLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses only REI's audited recipe-sync callbacks for the exact MM2 request.
 * REI's Forge HEAD hook may emit a packet-thread START before Minecraft reschedules the
 * packet, followed by the authoritative render-thread START and END pair.
 */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.RoughlyEnoughItemsCoreClient", remap = false)
public abstract class ReiNativeRecipeReloadMixin {
    @Inject(
            method = "lambda$registerEvents$8(Lnet/minecraft/world/item/crafting/RecipeManager;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void reiexport$suppressNativeStart(
            RecipeManager manager,
            CallbackInfo callback
    ) {
        if (Mm2ReiLifecycleGate.suppressNativeStart(manager)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "lambda$registerEvents$9(Lorg/apache/commons/lang3/mutable/MutableLong;Lnet/minecraft/world/item/crafting/RecipeManager;)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void reiexport$suppressNativeEnd(
            MutableLong lastReload,
            RecipeManager manager,
            CallbackInfo callback
    ) {
        if (Mm2ReiLifecycleGate.suppressNativeEnd(manager)) {
            callback.cancel();
        }
    }
}
