package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClock;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClockContract;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freezes vanilla glint translation only inside an exact-MM2 exporter offscreen capture. */
@Mixin(value = RenderStateShard.class, remap = false)
public abstract class RenderStateShardGlintClockMixin {
    @Redirect(
            method = Mm2OffscreenGlintClockContract.SETUP_GLINT_TEXTURING_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = Mm2OffscreenGlintClockContract.UTIL_GET_MILLIS_INVOKE,
                    ordinal = 0,
                    remap = false),
            require = 1,
            remap = false)
    private static long reiexport$canonicalOffscreenGlintMillis() {
        if (!Mm2OffscreenGlintClock.isCaptureActive()) {
            return Util.getMillis();
        }
        return Mm2OffscreenGlintClock.canonicalGlintMillis();
    }
}
