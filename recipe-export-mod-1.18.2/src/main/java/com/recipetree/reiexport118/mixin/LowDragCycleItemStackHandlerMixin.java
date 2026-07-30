package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import com.recipetree.reiexport118.compat.Mm2LowDragCycleSelectionRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Removes LowDrag's wall-clock exemplar selection only for exact MM2 exporter requests. */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib.utils.CycleItemStackHandler", remap = false)
public abstract class LowDragCycleItemStackHandlerMixin {
    @Redirect(
            method = "getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/System;currentTimeMillis()J"),
            require = 1,
            remap = false)
    private long reiexport$selectFirstCandidate() {
        Mm2DeterminismCompatibility.requireArmed(
                Mm2DeterminismContract.LOW_DRAG_LIB.modId());
        return Mm2LowDragCycleSelectionRepair.firstCandidateEpochMillis();
    }
}
