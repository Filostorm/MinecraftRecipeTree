package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.client.config.ConfigObject;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.registry.ReloadStage;
import me.shedaniel.rei.impl.common.plugins.ReloadInterruptionContext;
import me.shedaniel.rei.impl.common.plugins.ReloadManagerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.crafting.RecipeManager;

/** Owns the single deterministic post-sync REI reload used by the exact MM2 profile. */
public final class Mm2ReiLifecycleGate {
    private static final Mm2ReiLifecycleSequence SEQUENCE = new Mm2ReiLifecycleSequence();

    private Mm2ReiLifecycleGate() {
    }

    static void arm() {
        SEQUENCE.arm();
        ReiExportMod.LOGGER.info(
                "[reiexport] Armed exact-once MM2 REI post-sync lifecycle gate");
    }

    public static boolean suppressNativeStart(RecipeManager manager) {
        return suppress(Mm2ReiLifecycleSequence.NativeStage.START, manager);
    }

    public static boolean suppressNativeEnd(RecipeManager manager) {
        return suppress(Mm2ReiLifecycleSequence.NativeStage.END, manager);
    }

    private static boolean suppress(
            Mm2ReiLifecycleSequence.NativeStage stage,
            RecipeManager manager
    ) {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            boolean minecraftThread = minecraft.isSameThread();
            boolean renderThread = RenderSystem.isOnRenderThread();
            if (minecraftThread != renderThread) {
                throw new IllegalStateException(
                        "Minecraft and RenderSystem disagree about native REI callback thread role: "
                                + Thread.currentThread().getName());
            }
            ClientPacketListener connection = minecraft.getConnection();
            Mm2ReiLifecycleSequence.NativeThreadRole role = renderThread
                    ? Mm2ReiLifecycleSequence.NativeThreadRole.RENDER
                    : Mm2ReiLifecycleSequence.NativeThreadRole.PACKET;
            // The first HEAD pass can belong to the incoming listener while Minecraft still
            // publishes its prior connection. The sequence binds this manager identity to the
            // rescheduled render pass; only then is the listener authoritative and comparable.
            if (role == Mm2ReiLifecycleSequence.NativeThreadRole.RENDER
                    && (connection == null || manager != connection.getRecipeManager())) {
                throw new IllegalStateException(
                        "render-thread native REI callback RecipeManager does not belong to "
                                + "the active client connection");
            }
            boolean suppressed = SEQUENCE.suppressNative(
                    stage, role, manager, Thread.currentThread());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Suppressed premature native REI recipe reload stage={} role={} managerIdentity={} thread={}",
                    stage, role, System.identityHashCode(manager),
                    Thread.currentThread().getName());
            return suppressed;
        } catch (RuntimeException | Error failure) {
            fail("suppressing premature native REI recipe reload", failure);
            throw failure;
        }
    }

    public static void reloadAfterRecipeSync(ClientPacketListener connection) {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (connection == null || connection != minecraft.getConnection()) {
                throw new IllegalStateException(
                        "recipe-sync callback does not belong to the active client connection");
            }
            if (!minecraft.isSameThread() || !RenderSystem.isOnRenderThread()) {
                throw new IllegalStateException(
                        "owned MM2 REI reload is not executing on Minecraft's render thread: "
                                + Thread.currentThread().getName());
            }
            RecipeManager manager = connection.getRecipeManager();
            requireIdle("before owned reload");
            if (ConfigObject.getInstance().doesRegisterRecipesInAnotherThread()) {
                throw new IllegalStateException(
                        "REI registerRecipesInAnotherThread must remain false for the exact MM2 export");
            }

            SEQUENCE.beginOwnedReload(manager, Thread.currentThread());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Starting one owned synchronous REI reload after authoritative recipe sync managerIdentity={}",
                    System.identityHashCode(manager));
            ReloadManagerImpl.reloadPlugins(null, ReloadInterruptionContext.ofNever());
            requireIdle("after owned reload");
            Mm2JeiDeferredTaskGate.requireComplete();
            Mm2PigmentRecipeRegistrationGate.requireComplete();
            KubeJsTooltipPublicationRepair.requirePublishedSnapshot();
            Mm2RegistryRepairs.repairAndVerifyAfterOwnedReload();
            Mm2MultiblockedCycleStateRepair.requireObservedAfterOwnedReiReload();
            SEQUENCE.completeOwnedReload(Thread.currentThread());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Completed exact-once MM2 REI post-sync reload: stages=START,END outstandingTasks=0");
        } catch (RuntimeException | Error failure) {
            fail("executing the owned post-sync REI reload", failure);
            throw failure;
        }
    }

    public static void onReloadStageEnter(ReloadStage stage) {
        if (!isOwnedStage(stage)) {
            return;
        }
        try {
            SEQUENCE.enterReloadStage(sequenceStage(stage), Thread.currentThread());
            Mm2JeiDeferredTaskGate.beginStage(stage);
            Mm2PigmentRecipeRegistrationGate.beginStage(stage);
        } catch (RuntimeException | Error failure) {
            fail("entering owned REI stage " + stage, failure);
            throw failure;
        }
    }

    public static void onReloadStageExit(ReloadStage stage) {
        if (!isOwnedStage(stage)) {
            return;
        }
        try {
            requireIdleManagersOnly("after owned REI stage " + stage);
            Mm2JeiDeferredTaskGate.finishStage(stage);
            Mm2PigmentRecipeRegistrationGate.finishStage(stage);
            KubeJsTooltipPublicationRepair.PublishedSnapshot snapshot =
                    KubeJsTooltipPublicationRepair.requirePublishedSnapshot();
            SEQUENCE.exitReloadStage(
                    sequenceStage(stage),
                    Thread.currentThread(),
                    new Mm2ReiLifecycleSequence.Publication(
                            snapshot.generation(), snapshot.handlers()));
        } catch (RuntimeException | Error failure) {
            fail("exiting owned REI stage " + stage, failure);
            throw failure;
        }
    }

    public static void requireCompleteForExport() {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            throw new IllegalStateException(
                    "MM2 export request reached the exporter without its exact compatibility preflight");
        }
        SEQUENCE.requireComplete();
        Mm2JeiDeferredTaskGate.requireComplete();
        Mm2PigmentRecipeRegistrationGate.requireComplete();
    }

    /** Converts REI's otherwise swallowed plugin failure into a terminal owned-reload failure. */
    public static void rejectSwallowedPluginFailure(String seam, Throwable failure) {
        if (SEQUENCE.state() != Mm2ReiLifecycleSequence.State.OWNED_RELOAD) {
            return;
        }
        IllegalStateException rejection = new IllegalStateException(
                "REI swallowed a plugin failure during the exporter-owned reload at " + seam,
                failure);
        fail("auditing swallowed REI plugin failure at " + seam, rejection);
        throw rejection;
    }

    /** Exact compatibility-mixin scope; false for native, unrelated, failed, or complete reloads. */
    public static boolean isOwnedReloadActiveForCompatibility() {
        return Mm2DeterminismCompatibility.isLifecycleArmed()
                && SEQUENCE.state() == Mm2ReiLifecycleSequence.State.OWNED_RELOAD;
    }

    private static boolean isOwnedStage(ReloadStage stage) {
        return isOwnedReloadActiveForCompatibility()
                && stage != null;
    }

    private static Mm2ReiLifecycleSequence.ReloadStage sequenceStage(ReloadStage stage) {
        if (stage == ReloadStage.START) {
            return Mm2ReiLifecycleSequence.ReloadStage.START;
        }
        if (stage == ReloadStage.END) {
            return Mm2ReiLifecycleSequence.ReloadStage.END;
        }
        throw new IllegalStateException("Unsupported REI reload stage: " + stage);
    }

    private static void requireIdle(String context) {
        int running = ReloadManagerImpl.countRunningReloadTasks();
        int uninterrupted = ReloadManagerImpl.countUninterruptedRunningReloadTasks();
        if (running != 0 || uninterrupted != 0 || PluginManager.areAnyReloading()) {
            throw new IllegalStateException("REI is not idle " + context
                    + ": runningTasks=" + running
                    + ", uninterruptedTasks=" + uninterrupted
                    + ", managersReloading=" + PluginManager.areAnyReloading());
        }
    }

    private static void requireIdleManagersOnly(String context) {
        if (PluginManager.areAnyReloading()) {
            throw new IllegalStateException("REI plugin manager remained reloading " + context);
        }
    }

    private static void fail(String context, Throwable failure) {
        SEQUENCE.fail(failure);
        Mm2JeiDeferredTaskGate.fail(failure);
        Mm2PigmentRecipeRegistrationGate.fail(failure);
        ReiExportMod.LOGGER.error(
                "[reiexport] MM2 exact-once REI lifecycle failed while {}; no retry or fallback was attempted",
                context, failure);
    }
}
