package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import me.shedaniel.rei.impl.common.InternalLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rejects per-plugin failures that REI normally logs and then suppresses. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.common.plugins.PluginManagerImpl", remap = false)
public abstract class ReiPluginErrorLedgerMixin {
    @Redirect(
            method = "pluginSection",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/shedaniel/rei/impl/common/InternalLogger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            require = 1,
            remap = false)
    private void reiexport$rejectSwallowedPluginSectionFailure(
            InternalLogger logger,
            String message,
            Throwable failure
    ) {
        logger.error(message, failure);
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "PluginManagerImpl.pluginSection", failure);
    }
}
