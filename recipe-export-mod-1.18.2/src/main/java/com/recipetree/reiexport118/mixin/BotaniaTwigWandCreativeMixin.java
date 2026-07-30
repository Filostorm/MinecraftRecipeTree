package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismContract;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Botania's shuffled seven-wand sample with the complete stable domain. */
@Pseudo
@Mixin(targets = Mm2DeterminismContract.BOTANIA_TWIG_WAND_CLASS, remap = false)
public abstract class BotaniaTwigWandCreativeMixin extends Item {
    @Unique
    private static final String REIEXPORT$FILL =
            "m_6787_(Lnet/minecraft/world/item/CreativeModeTab;Lnet/minecraft/core/NonNullList;)V";
    @Unique
    private static final int[][] REIEXPORT$COLOR_PAIRS = {
            {0, 3}, {0, 6}, {3, 6}, {10, 11}, {14, 14}, {11, 11},
            {1, 1}, {15, 15}, {7, 8}, {6, 7}, {4, 5}, {0, 15}
    };

    protected BotaniaTwigWandCreativeMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = REIEXPORT$FILL, at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void reiexport$publishCompleteStableWandDomain(
            CreativeModeTab tab,
            NonNullList<ItemStack> stacks,
            CallbackInfo callbackInfo
    ) {
        Mm2DeterminismCompatibility.requireArmed(Mm2DeterminismContract.BOTANIA.modId());
        if (allowdedIn(tab)) {
            stacks.add(reiexport$wand(this, 0, 0));
            for (int[] pair : REIEXPORT$COLOR_PAIRS) {
                stacks.add(reiexport$wand(this, pair[0], pair[1]));
            }
        }
        callbackInfo.cancel();
        ReiExportMod.LOGGER.debug(
                "[reiexport] Published Botania's exact deterministic wand exemplar domain (13 entries)");
    }

    @Unique
    private static ItemStack reiexport$wand(Item item, int first, int second) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putInt("color1", first);
        stack.getOrCreateTag().putInt("color2", second);
        return stack;
    }
}
