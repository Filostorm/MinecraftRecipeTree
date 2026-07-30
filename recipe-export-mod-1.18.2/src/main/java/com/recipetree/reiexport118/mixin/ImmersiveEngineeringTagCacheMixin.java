package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2IePreferredTagCacheRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.function.Function;

/** Protects the one IE cache mutation reached by parallel arc-recipe layout conversion. */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.api.IEApi", remap = false)
public abstract class ImmersiveEngineeringTagCacheMixin {
    private static final String GET_PREFERRED_TAG_STACK =
            "getPreferredTagStack(Lnet/minecraft/core/RegistryAccess;"
                    + "Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/item/ItemStack;";

    @Redirect(
            method = GET_PREFERRED_TAG_STACK,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/HashMap;computeIfAbsent(Ljava/lang/Object;"
                            + "Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1,
            remap = false)
    private static Object reiexport$synchronizePreferredTagCache(
            HashMap<Object, Object> cache,
            Object key,
            Function<Object, Object> mappingFunction
    ) {
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.IMMERSIVE_ENGINEERING.modId());
        return Mm2IePreferredTagCacheRepair.compute(cache, key, mappingFunction);
    }
}
