package com.recipetree.reiexport118.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the already-owned RGBA allocation to the direct STB filename encoder.
 *
 * <p>Minecraft's {@link NativeImage#writeToFile(java.nio.file.Path)} creates an executable
 * {@code STBIWriteCallback} trampoline for every image. Large x86_64 exports under Rosetta can
 * exhaust Rosetta's translated callback-fragment bookkeeping. The direct filename API consumes
 * the same native allocation without allocating a callback.</p>
 */
@Mixin(NativeImage.class)
public interface NativeImagePixelsAccessor {
    @Accessor(value = "f_84964_", remap = false)
    long reiexport$getPixels();
}
