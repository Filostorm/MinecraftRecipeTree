package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2JeiIngredientTypeCacheRepair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Makes the pinned JEI ingredient-type cache safe for REI's optimized worker partitions. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.jeicompat.JEIPluginDetector", remap = false)
public abstract class JeiPluginDetectorTypeCacheMixin {
    @Shadow(remap = false)
    @Final
    @Mutable
    private static Map<Object, Object> TYPE_MAP;

    @Inject(method = "<clinit>", at = @At("RETURN"), require = 1, remap = false)
    private static void reiexport$installConcurrentIngredientTypeCache(CallbackInfo callback) {
        TYPE_MAP = Mm2JeiIngredientTypeCacheRepair.install(TYPE_MAP);
    }
}
