package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import me.shedaniel.rei.impl.common.plugins.ReloadInterruptionContext;
import me.shedaniel.rei.impl.common.InternalLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Audits the exact START/END sequence inside the exporter-owned synchronous REI reload. */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl", remap = false)
public abstract class ReiReloadLifecycleMixin {
    private static final String TARGET =
            "reloadPlugins0(Lme/shedaniel/rei/api/common/registry/ReloadStage;"
                    + "Lme/shedaniel/rei/impl/common/plugins/ReloadInterruptionContext;)V";

    @Inject(method = TARGET, at = @At("HEAD"), require = 1, remap = false)
    private static void reiexport$enterOwnedStage(
            ReloadStage stage,
            ReloadInterruptionContext interruption,
            CallbackInfo callback
    ) {
        Mm2ReiLifecycleGate.onReloadStageEnter(stage);
    }

    @Inject(method = TARGET, at = @At("RETURN"), require = 1, remap = false)
    private static void reiexport$exitOwnedStage(
            ReloadStage stage,
            ReloadInterruptionContext interruption,
            CallbackInfo callback
    ) {
        Mm2ReiLifecycleGate.onReloadStageExit(stage);
    }

    @Redirect(
            method = "reloadPlugins0(Lme/shedaniel/rei/impl/common/plugins/PluginReloadContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/shedaniel/rei/impl/common/InternalLogger;throwException(Ljava/lang/Throwable;)V"),
            require = 1,
            remap = false)
    private static void reiexport$rejectSwallowedReloadFailure(
            InternalLogger logger,
            Throwable failure
    ) {
        logger.throwException(failure);
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "ReloadManagerImpl.reloadPlugins0", failure);
    }

    @Redirect(
            method = "reloadPlugins0(Lme/shedaniel/rei/impl/common/plugins/PluginReloadContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lme/shedaniel/rei/impl/common/InternalLogger;debug(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            require = 1,
            remap = false)
    private static void reiexport$rejectSwallowedReloadInterruption(
            InternalLogger logger,
            String message,
            Throwable failure
    ) {
        logger.debug(message, failure);
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "ReloadManagerImpl.reloadPlugins0 interruption", failure);
    }
}
