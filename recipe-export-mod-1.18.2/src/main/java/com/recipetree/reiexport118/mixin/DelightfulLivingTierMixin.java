package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.compat.DelightfulLivingTierContract;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = DelightfulLivingTierContract.TIER_CLASS, remap = false)
public abstract class DelightfulLivingTierMixin {
    @Unique
    private static final String CONSTRUCTOR = "<init>(Ljava/lang/String;IIIFFILjava/util/function/Supplier;)V";

    @Mutable
    @Final
    @Shadow(remap = false)
    private Supplier<Ingredient> repairIngredient;

    @Inject(method = CONSTRUCTOR, at = @At("TAIL"), require = 1, remap = false)
    private void reiexport$repairLivingTierContract(
            String enumName,
            int ordinal,
            int level,
            int uses,
            float speed,
            float attackDamageBonus,
            int enchantability,
            Supplier<Ingredient> constructorRepairIngredient,
            CallbackInfo callbackInfo
    ) {
        if (!"LIVING".equals(enumName)) {
            return;
        }

        if (constructorRepairIngredient == null || this.repairIngredient != constructorRepairIngredient) {
            IllegalStateException exception = new IllegalStateException(
                    "drifted DelightfulTiers.LIVING repairIngredient field assignment"
            );
            ReiExportMod.LOGGER.error(
                    "[reiexport] Refusing unsafe Delightful null repair-ingredient correction: {}",
                    exception.getMessage()
            );
            throw exception;
        }

        final Ingredient current;
        try {
            current = constructorRepairIngredient.get();
        } catch (RuntimeException exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Refusing unsafe Delightful null repair-ingredient correction because the LIVING supplier threw",
                    exception
            );
            throw exception;
        }
        if (current != null) {
            return;
        }

        try {
            DelightfulLivingTierContract.requireExact(
                    enumName,
                    ordinal,
                    level,
                    uses,
                    speed,
                    attackDamageBonus,
                    enchantability
            );
        } catch (RuntimeException exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Refusing unsafe Delightful null repair-ingredient correction: {}",
                    exception.getMessage()
            );
            throw exception;
        }

        // Allocate this once on the sole LIVING enum construction. Keeping it constructor-local
        // avoids depending on merged static-initializer ordering while Delightful builds its enum.
        this.repairIngredient = () -> Ingredient.EMPTY;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Corrected Delightful 2.6 {} self-repair-only Tier contract from null to Ingredient.EMPTY ({})",
                DelightfulLivingTierContract.ITEM_ID,
                DelightfulLivingTierContract.TIER_CLASS + ".LIVING"
        );
    }
}
