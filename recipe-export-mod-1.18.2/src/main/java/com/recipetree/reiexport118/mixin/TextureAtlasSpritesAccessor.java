package com.recipetree.reiexport118.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes the exact stitched sprite inventory for a one-time native first-frame upload. */
@Mixin(value = TextureAtlas.class, remap = false)
public interface TextureAtlasSpritesAccessor {
    @Accessor(value = "f_118264_", remap = false)
    Map<ResourceLocation, TextureAtlasSprite> reiexport$getTexturesByName();
}
