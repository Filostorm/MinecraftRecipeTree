package com.recipetree.reiexport118.mixin;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2MultiblockedCycleStateRepair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pins Multiblocked's shared random exemplar for the dedicated exact-request process session.
 * Process scope is required because the owned REI reload samples displays before ExportJob exists.
 */
@Pseudo
@Mixin(
        targets = "com.lowdragmc.multiblocked.client.renderer.impl.CycleBlockStateRenderer",
        remap = false)
public abstract class MultiblockedCycleBlockStateRendererMixin {
    @Shadow(remap = false)
    @Final
    public BlockInfo[] blockInfos;

    @Inject(
            method = "getBlockInfo()Lcom/lowdragmc/lowdraglib/utils/BlockInfo;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private void reiexport$selectFirstCandidate(
            CallbackInfoReturnable<BlockInfo> callback
    ) {
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.MULTIBLOCKED.modId());
        callback.setReturnValue(
                Mm2MultiblockedCycleStateRepair.firstCandidate(blockInfos));
    }
}
