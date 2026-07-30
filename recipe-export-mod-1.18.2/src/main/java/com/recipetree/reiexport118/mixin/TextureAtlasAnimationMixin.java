package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2BlockAtlasCanonicalization;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only the active export job's exact block-atlas animation cycle. */
@Mixin(value = TextureAtlas.class, remap = false)
public abstract class TextureAtlasAnimationMixin {
    @Inject(
            method = "m_118270_()V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private void reiexport$holdCanonicalFrames(CallbackInfo callback) {
        if (Mm2BlockAtlasCanonicalization.suppressCycleIfScoped(
                (TextureAtlas) (Object) this)) {
            callback.cancel();
        }
    }
}
