package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.compat.TopArmorMaterialContract;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorMaterials;
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

@Pseudo
@Mixin(targets = "mcjty.theoneprobe.items.TopArmorMaterial", remap = false)
public abstract class TopArmorMaterialMixin {
    @Unique
    private static final String CONSTRUCTOR = "<init>(Ljava/lang/String;I[IILnet/minecraft/sounds/SoundEvent;FLnet/minecraft/world/item/crafting/Ingredient;)V";

    @Mutable
    @Final
    @Shadow(remap = false)
    private Ingredient repairMaterial;

    @Inject(method = CONSTRUCTOR, at = @At("TAIL"), require = 1, remap = false)
    private void reiexport$repairNullIngredient(
            String internalName,
            int durability,
            int[] damageReduction,
            int enchantability,
            SoundEvent soundEvent,
            float toughness,
            Ingredient constructorRepairMaterial,
            CallbackInfo callbackInfo
    ) {
        if (constructorRepairMaterial != null) {
            return;
        }

        ResourceLocation soundKey = Registry.SOUND_EVENT.getKey(soundEvent);
        String soundId = soundKey == null ? "<unregistered>" : soundKey.toString();
        final TopArmorMaterialContract.Target target;
        try {
            target = TopArmorMaterialContract.requireExact(
                    internalName,
                    durability,
                    damageReduction,
                    enchantability,
                    soundId,
                    toughness
            );
        } catch (RuntimeException exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Refusing unsafe The One Probe null repair-material correction: {}",
                    exception.getMessage()
            );
            throw exception;
        }

        Ingredient corrected = switch (target) {
            case DIAMOND -> ArmorMaterials.DIAMOND.getRepairIngredient();
            case GOLD -> ArmorMaterials.GOLD.getRepairIngredient();
            case IRON -> ArmorMaterials.IRON.getRepairIngredient();
        };
        if (corrected == null) {
            IllegalStateException exception = new IllegalStateException(
                    "vanilla " + target + " armor material unexpectedly returned a null repair ingredient"
            );
            ReiExportMod.LOGGER.error("[reiexport] {}", exception.getMessage());
            throw exception;
        }

        this.repairMaterial = corrected;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Corrected The One Probe 5.1.2 {} null repair ingredient using ArmorMaterials.{}",
                target.internalName(),
                target
        );
    }
}
