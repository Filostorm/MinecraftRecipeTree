package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2CreativeExemplarRepair;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Materializes the exact empty IF tank map before read-side capability mutation. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.INFINITY_BACKPACK_CLASS, remap = false)
public abstract class IndustrialForegoingBackpackNbtMixin {
    @Unique
    private static final String REIEXPORT$ADD_NBT =
            "addNbt(Lnet/minecraft/world/item/ItemStack;JIZ)V";

    @Inject(method = REIEXPORT$ADD_NBT, at = @At("RETURN"), require = 1, remap = false)
    private void reiexport$materializeEmptyTanks(
            ItemStack stack,
            long energy,
            int fluidAmount,
            boolean special,
            CallbackInfo callbackInfo
    ) {
        Mm2CreativeExemplarRepair.ensureIfEmptyTanks((Item) (Object) this, stack);
    }
}
