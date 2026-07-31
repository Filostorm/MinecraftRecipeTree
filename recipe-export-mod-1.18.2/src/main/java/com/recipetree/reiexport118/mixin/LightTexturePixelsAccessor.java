package com.recipetree.reiexport118.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.recipetree.reiexport118.compat.Mm2LightmapReadinessContract;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the CPU-side 16x16 lightmap solely for exact-MM2 readiness and capture audits. */
@Mixin(value = LightTexture.class, remap = false)
public interface LightTexturePixelsAccessor {
    @Accessor(value = Mm2LightmapReadinessContract.LIGHT_PIXELS_FIELD, remap = false)
    NativeImage reiexport$getLightPixels();
}
