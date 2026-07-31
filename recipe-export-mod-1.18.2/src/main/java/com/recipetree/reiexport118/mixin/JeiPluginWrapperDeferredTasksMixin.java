package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.Mm2JeiDeferredTaskGate;
import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Makes the pinned JEI compatibility wrapper's END pass the sole deferred-task generation.
 */
@Pseudo
@Mixin(
        targets = "me.shedaniel.rei.jeicompat.JEIPluginDetector$JEIPluginWrapper",
        remap = false)
public abstract class JeiPluginWrapperDeferredTasksMixin {
    @Unique
    private static final String POST_STAGE =
            "postStage(Lme/shedaniel/rei/api/common/plugins/PluginManager;"
                    + "Lme/shedaniel/rei/api/common/registry/ReloadStage;)V";
    @Unique
    private static final String REGISTER_CATEGORIES =
            "registerCategories(Lme/shedaniel/rei/api/client/registry/category/"
                    + "CategoryRegistry;)V";
    @Unique
    private static final String REGISTER_DISPLAYS =
            "registerDisplays(Lme/shedaniel/rei/api/client/registry/display/"
                    + "DisplayRegistry;)V";
    @Unique
    private static final String REGISTER_TRANSFER_HANDLERS =
            "registerTransferHandlers(Lme/shedaniel/rei/api/client/registry/transfer/"
                    + "TransferHandlerRegistry;)V";

    @Shadow(remap = false)
    @Final
    private List<Runnable> entryRegistry;

    @Shadow(remap = false)
    @Final
    private List<Runnable> post;

    @Shadow(remap = false)
    public abstract String getPluginProviderName();

    @Inject(method = REGISTER_CATEGORIES, at = @At("HEAD"), require = 1, remap = false)
    private void reiexport$observeRegisterCategories(
            CategoryRegistry registry,
            CallbackInfo callback
    ) {
        if (Mm2ReiLifecycleGate.isOwnedReloadActiveForCompatibility()) {
            Mm2JeiDeferredTaskGate.observeRegisterCategories(
                    this, getPluginProviderName());
        }
    }

    @Inject(method = REGISTER_DISPLAYS, at = @At("HEAD"), require = 1, remap = false)
    private void reiexport$observeRegisterDisplays(
            DisplayRegistry registry,
            CallbackInfo callback
    ) {
        if (Mm2ReiLifecycleGate.isOwnedReloadActiveForCompatibility()) {
            Mm2JeiDeferredTaskGate.observeRegisterDisplays(
                    this, getPluginProviderName());
        }
    }

    @Inject(method = REGISTER_TRANSFER_HANDLERS, at = @At("HEAD"), require = 1, remap = false)
    private void reiexport$observeRegisterTransferHandlers(
            TransferHandlerRegistry registry,
            CallbackInfo callback
    ) {
        if (Mm2ReiLifecycleGate.isOwnedReloadActiveForCompatibility()) {
            Mm2JeiDeferredTaskGate.observeRegisterTransferHandlers(
                    this, getPluginProviderName());
        }
    }

    @Inject(method = POST_STAGE, at = @At("HEAD"), require = 1, remap = false)
    private void reiexport$beginAuthoritativeDeferredTasks(
            PluginManager<?> manager,
            ReloadStage stage,
            CallbackInfo callback
    ) {
        if (reiexport$isOwnedClientReload(manager) && stage == ReloadStage.END) {
            Mm2JeiDeferredTaskGate.beginAuthoritativeWrapper(
                    this, getPluginProviderName(), entryRegistry, post);
        }
    }

    @Inject(method = POST_STAGE, at = @At("RETURN"), require = 1, remap = false)
    private void reiexport$finishDeferredTasks(
            PluginManager<?> manager,
            ReloadStage stage,
            CallbackInfo callback
    ) {
        if (reiexport$isOwnedClientReload(manager)) {
            Mm2JeiDeferredTaskGate.finishWrapper(
                    this, getPluginProviderName(), entryRegistry, post, stage);
        }
    }

    @Redirect(
            method = POST_STAGE,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Throwable;printStackTrace()V",
                    ordinal = 0),
            require = 1,
            remap = false)
    private void reiexport$rejectSwallowedEntryRegistryFailure(Throwable failure) {
        failure.printStackTrace();
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "JEIPluginWrapper.postStage entryRegistry task", failure);
    }

    @Redirect(
            method = POST_STAGE,
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Throwable;printStackTrace()V",
                    ordinal = 1),
            require = 1,
            remap = false)
    private void reiexport$rejectSwallowedPostFailure(Throwable failure) {
        failure.printStackTrace();
        Mm2ReiLifecycleGate.rejectSwallowedPluginFailure(
                "JEIPluginWrapper.postStage post task", failure);
    }

    @Unique
    private static boolean reiexport$isOwnedClientReload(PluginManager<?> manager) {
        return Mm2ReiLifecycleGate.isOwnedReloadActiveForCompatibility()
                && manager == PluginManager.getClientInstance();
    }
}
