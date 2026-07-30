package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2LightmapReadiness;
import com.recipetree.reiexport118.compat.Mm2LightmapReadinessContract;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records only vanilla lightmap recomputations that reached their native texture upload. */
@Mixin(value = LightTexture.class, remap = false)
public abstract class LightTextureUpdateMixin {
    @Inject(
            method = Mm2LightmapReadinessContract.UPDATE_LIGHT_TEXTURE_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = Mm2LightmapReadinessContract.DYNAMIC_TEXTURE_UPLOAD_INVOKE,
                    shift = At.Shift.AFTER,
                    remap = false),
            require = 1,
            remap = false)
    private void reiexport$recordCompletedLightmapUpdate(
            float partialTick,
            CallbackInfo callback
    ) {
        Mm2LightmapReadiness.recordCompletedVanillaUpdate(
                (LightTexture) (Object) this);
    }
}
