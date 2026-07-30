package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2SpiritEntityRenderDeterminism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Canonicalizes Spirit's two process-age render inputs on the JEI renderer selected by MM2's
 * {@code spirit:jei_jei_compat_entityingredient} REI entry type.
 */
@Pseudo
@Mixin(
        targets = "me.codexadrian.spirit.compat.jei.ingredients.EntityRenderer",
        remap = false)
public abstract class SpiritJeiEntityRendererMixin {
    @Redirect(
            method = "renderEntity(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/Level;FFFF)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;f_19797_:I",
                    opcode = 180),
            require = 1,
            remap = false)
    private static int reiexport$canonicalEntityTickCount(LocalPlayer player) {
        int upstream = player.tickCount;
        if (!Mm2SpiritEntityRenderDeterminism.isCaptureActive()) {
            return upstream;
        }
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.SPIRIT.modId());
        return Mm2SpiritEntityRenderDeterminism.entityTickCount(upstream);
    }

    @Redirect(
            method = "renderEntity(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/Level;FFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;m_91296_()F"),
            require = 1,
            remap = false)
    private static float reiexport$canonicalFrameTime(Minecraft minecraft) {
        float upstream = minecraft.getFrameTime();
        if (!Mm2SpiritEntityRenderDeterminism.isCaptureActive()) {
            return upstream;
        }
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.SPIRIT.modId());
        return Mm2SpiritEntityRenderDeterminism.frameTime(upstream);
    }
}
