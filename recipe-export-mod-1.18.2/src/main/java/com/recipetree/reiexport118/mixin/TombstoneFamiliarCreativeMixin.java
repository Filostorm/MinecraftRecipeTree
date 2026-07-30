package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2CreativeExemplarRepair;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces only Tombstone's randomized second creative receptacle with a cat. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.TOMBSTONE_RECEPTACLE_CLASS, remap = false)
public abstract class TombstoneFamiliarCreativeMixin {
    @Unique
    private static final String REIEXPORT$FILL =
            "m_6787_(Lnet/minecraft/world/item/CreativeModeTab;Lnet/minecraft/core/NonNullList;)V";
    @Unique
    private static final String REIEXPORT$RANDOM_FAMILIAR =
            "Lovh/corail/tombstone/item/ItemReceptacleOfFamiliar;"
                    + "setRandomFamiliar(Lnet/minecraft/world/item/ItemStack;)"
                    + "Lnet/minecraft/world/item/ItemStack;";

    @Redirect(
            method = REIEXPORT$FILL,
            at = @At(value = "INVOKE", target = REIEXPORT$RANDOM_FAMILIAR),
            require = 1,
            remap = false
    )
    private ItemStack reiexport$createExactCatExemplar(
            @Coerce Object receiver,
            ItemStack stack
    ) {
        if (receiver != this) {
            throw new IllegalStateException(
                    "Tombstone creative familiar invocation receiver drift");
        }
        return Mm2CreativeExemplarRepair.createTombstoneCatExemplar(
                (Item) (Object) this, stack);
    }
}
