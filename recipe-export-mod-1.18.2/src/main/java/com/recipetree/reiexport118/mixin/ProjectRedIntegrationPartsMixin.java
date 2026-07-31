package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2ProjectRedRegistrationGate;
import mrtjp.projectred.integration.GateType;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents Integration from conditionally duplicating Fabrication's fabricated-gate item. */
@Pseudo
@Mixin(targets = "mrtjp.projectred.integration.init.IntegrationParts", remap = false)
public abstract class ProjectRedIntegrationPartsMixin {
    @Redirect(
            method = "register()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmrtjp/projectred/integration/GateType;isEnabled()Z"),
            require = 1,
            remap = false)
    private static boolean reiexport$skipDuplicateFabricatedGate(GateType gateType) {
        boolean upstreamEnabled = gateType.isEnabled();
        boolean fabricatedGate = gateType == GateType.FABRICATED_GATE;
        if (fabricatedGate) {
            ReiExportMixinConfigPlugin.requireExactProjectRedRegistrationSelection(
                    FMLLoader.getGamePath());
        }
        return Mm2ProjectRedRegistrationGate.filterRegistration(
                fabricatedGate, upstreamEnabled);
    }
}
