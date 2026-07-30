package com.recipetree.reiexport118;

import com.mojang.logging.LogUtils;
import com.recipetree.reiexport118.compat.CapsuleRecipeSyncCompatibility;
import com.recipetree.reiexport118.compat.FlowerEffectPreflight;
import com.recipetree.reiexport118.compat.IndustrialForegoingScreenCompatibility;
import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyCompatibility;
import com.recipetree.reiexport118.compat.LaserIoJeiRuntimeCompatibility;
import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.Mm2BlockAtlasCanonicalizationCompatibility;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.NativeSpriteIconCompatibility;
import com.recipetree.reiexport118.compat.RepairIngredientPreflight;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(ReiExportMod.MOD_ID)
public final class ReiExportMod {
    public static final String MOD_ID = "reiexport";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ReiExportMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            if (!Mm2DeterminismCompatibility.validateBeforeReiRegistration()) {
                return;
            }
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
            MinecraftForge.EVENT_BUS.register(ExportEvents.class);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        CapsuleRecipeSyncCompatibility.validateBeforeReiRegistration();
        KubeJsTooltipConcurrencyCompatibility.validateBeforeReiRegistration();
        IndustrialForegoingScreenCompatibility.validateBeforeReiRegistration();
        LowDragFboViewportCompatibility.validateBeforeReiRegistration();
        NativeSpriteIconCompatibility.validateBeforeReiRegistration();
        Mm2BlockAtlasCanonicalizationCompatibility.validateBeforeExport();
        LaserIoJeiRuntimeCompatibility.validateBeforeReiRegistration();
        FlowerEffectPreflight.repairAndValidateBeforeReiRegistration();
        RepairIngredientPreflight.validateBeforeReiRegistration();
    }
}
