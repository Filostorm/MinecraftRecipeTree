package com.recipetree.reiexport118.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2SpiritEntityRenderDeterminism;
import com.recipetree.reiexport118.compat.Mm2SpiritShaderGameTimeContract;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pins only Spirit's procedural corrupted-entity shader inside an owned MM2 native capture. */
@Mixin(value = BufferUploader.class, remap = false)
public abstract class BufferUploaderSpiritShaderClockMixin {
    @Redirect(
            method = Mm2SpiritShaderGameTimeContract.DRAW_WITH_SHADER_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = Mm2SpiritShaderGameTimeContract.SHADER_GAME_TIME_INVOKE,
                    ordinal = 0,
                    remap = false),
            require = 1,
            remap = false)
    private static float reiexport$canonicalSpiritShaderGameTime() {
        float upstream = RenderSystem.getShaderGameTime();
        if (!Mm2SpiritEntityRenderDeterminism.isCaptureActive()) {
            return upstream;
        }
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            throw new IllegalStateException(
                    "BufferUploader requested shader GameTime without an active ShaderInstance "
                            + "during an owned MM2 native capture");
        }
        if (!Mm2SpiritShaderGameTimeContract.CORRUPTED_ENTITY_SHADER
                .equals(shader.getName())) {
            return upstream;
        }
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.SPIRIT.modId());
        return Mm2SpiritEntityRenderDeterminism.corruptedShaderGameTime(
                upstream,
                shader.getName());
    }
}
