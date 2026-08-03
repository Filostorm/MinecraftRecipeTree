package com.recipetree.neiexport1710;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.event.NEIConfigsLoadedEvent;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import com.dreammaster.coremod.DreamCoreMod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Public because Forge 1.7.10 generates its event-bus trampoline in another
 * runtime package and class loader. A package-private subscriber class is not
 * accessible to that trampoline even when its subscribed method is public.
 */
public final class ExportCoordinator {
    private static final String EXPECTED_NEI_WORLD_PATH =
            "local/" + AutomationWorldBootstrap.WORLD_FOLDER;
    private ExportRequest activeRequest;
    private ExportJob activeJob;
    private long ticks;
    private int readinessTicks;
    private int stableTicks;
    private String previousFingerprint;
    private String previousVisibilityFingerprint;
    private String previousAdapterFingerprint;
    private List<ICraftingHandler> structurallyValidatedHandlers;
    private WorldClient previousWorld;
    private EntityPlayerSP previousPlayer;
    private List<ItemStack> previousItems;
    private int previousItemCount = -1;
    private boolean automationWorldLaunchRequested;
    private boolean ownedAutomationSessionValidated;
    private boolean liveServerWorldValidated;
    private boolean runtimePinsValidated;
    private volatile boolean neiConfigsLoadedObserved;
    private boolean neiConfigsLoadedLogged;
    private WorldClient exportWorld;
    private EntityPlayerSP exportPlayer;
    private List<ItemStack> exportItems;
    private int exportItemCount;
    private String exportHandlerFingerprint;
    private String exportVisibilityFingerprint;
    private String exportAdapterFingerprint;
    private int exitCountdown = -1;
    private boolean exitFailure;
    private SupervisedShutdownPolicy.RenderState exitRenderState;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick();
    }

    /**
     * NEI posts this from its plugin-loader thread. Only publish a volatile
     * lifecycle flag here; all registry inspection remains on the client tick.
     */
    @SubscribeEvent
    public void onNeiConfigsLoaded(NEIConfigsLoadedEvent event) {
        neiConfigsLoadedObserved = true;
    }

    private void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (exitCountdown >= 0 && --exitCountdown <= 0) {
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] {} one-shot automation; preserving the scheduled "
                            + "render-critical state through the current frame before requesting "
                            + "the outer game-loop exit ({})",
                    exitFailure ? "Failed" : "Completed", exitRenderState.describe());
            exitCountdown = -1;
            try {
                SupervisedShutdownPolicy.requireUnchangedRenderState(
                        exitRenderState,
                        minecraft.theWorld,
                        minecraft.thePlayer,
                        minecraft.isIntegratedServerRunning(),
                        minecraft.getIntegratedServer(),
                        DreamCoreMod.showConfirmExitWindow);
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Render-critical state is unchanged; requesting the "
                                + "Minecraft.run game loop to stop after this frame. The outer "
                                + "loop exclusively owns world, sound, renderer, display, and "
                                + "JVM cleanup.");
                minecraft.shutdown();
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Minecraft.shutdown returned with the DreamCore "
                                + "confirmation gate verified disabled; awaiting the supervised "
                                + "terminal-marker grace period for outer-loop process exit");
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] Refusing to request game-loop exit because the "
                                + "render-safe retained-state contract failed. The exporter will "
                                + "not mutate render-critical state or invoke final application "
                                + "cleanup from a client tick; the supervisor will fail the run "
                                + "when its terminal-marker grace period expires.",
                        error);
            }
            return;
        }
        if (exitCountdown >= 0) {
            return;
        }

        if (activeJob != null) {
            runJob();
            return;
        }
        if (activeRequest == null) {
            if (ticks % 20L == 1L && Boolean.parseBoolean(
                    System.getProperty("gtnh.neiexport.auto", "true"))) {
                pollRequest(minecraft);
            }
            return;
        }
        waitForNeiThenStart();
    }

    private void pollRequest(Minecraft minecraft) {
        Path gameDirectory = minecraft.mcDataDir.toPath().toAbsolutePath().normalize();
        String configured = System.getProperty("gtnh.neiexport.request", "neiexport-request.json");
        Path requestFile = java.nio.file.Paths.get(configured);
        if (!requestFile.isAbsolute()) {
            requestFile = gameDirectory.resolve(requestFile);
        }
        requestFile = requestFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(requestFile)) {
            return;
        }
        String fileName = requestFile.getFileName().toString();
        String stem = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5) : fileName;
        Path running = requestFile.resolveSibling(stem + ".running-" + UUID.randomUUID() + ".json");
        try {
            moveAtomicWithLoggedFallback(requestFile, running, false, "claim request");
            ExportRequest request = ExportRequest.fromFile(running, minecraft);
            request.runningMarker = running;
            activeRequest = request;
            readinessTicks = 0;
            automationWorldLaunchRequested = false;
            ownedAutomationSessionValidated = false;
            liveServerWorldValidated = false;
            runtimePinsValidated = false;
            resetReadinessStability();
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Claimed request {}; output={}", running, request.output);
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Invalid request {}; it will not be retried", running, error);
            finishMarker(running, false);
            scheduleExit(true);
        }
    }

    private void waitForNeiThenStart() {
        readinessTicks++;
        try {
            observeReadinessAndMaybeStart();
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            failBeforeStart("HANDLER_UNLOADED: runtime readiness observation failed: " + error);
        }
    }

    private void observeReadinessAndMaybeStart() throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        String upstreamOmission = GtnhNeiExportMod.NEI_FAILURE_MONITOR.failureSummary();
        if (upstreamOmission != null) {
            failBeforeStart("HANDLER_UNLOADED: " + upstreamOmission);
            return;
        }
        bootstrapAutomationWorldIfNeeded(minecraft);
        upstreamOmission = GtnhNeiExportMod.NEI_FAILURE_MONITOR.failureSummary();
        if (upstreamOmission != null) {
            failBeforeStart("HANDLER_UNLOADED: " + upstreamOmission);
            return;
        }

        WorldClient world = minecraft.theWorld;
        EntityPlayerSP player = minecraft.thePlayer;
        boolean worldReady = world != null;
        boolean playerReady = player != null;
        boolean creativePlayer = playerReady && minecraft.playerController != null
                && minecraft.playerController.isInCreativeMode();
        boolean overworldSession = worldReady && playerReady && world.provider != null
                && world.provider.dimensionId == 0 && player.dimension == 0;

        // Reading the volatile event flag first establishes visibility for the
        // plugin-loader writes that precede NEIConfigsLoadedEvent publication.
        boolean configsEvent = neiConfigsLoadedObserved;
        if (configsEvent && !neiConfigsLoadedLogged) {
            neiConfigsLoadedLogged = true;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Observed NEIConfigsLoadedEvent on the client tick; "
                            + "plugin registration boundary reached");
        }
        boolean neiLoaded = configsEvent && NEIClientConfig.isLoaded();
        boolean neiEnabled = neiLoaded && NEIClientConfig.isEnabled();
        String neiWorldPath = configsEvent ? NEIClientConfig.getWorldPath() : null;
        boolean neiWorldReady = EXPECTED_NEI_WORLD_PATH.equals(neiWorldPath);
        List<ItemStack> items = ItemList.items;
        int itemCount = items == null ? 0 : items.size();
        boolean itemListFinished = ItemList.loadFinished;
        HandlerSnapshot registered = snapshotRegisteredHandlers();
        boolean ready = worldReady && playerReady && creativePlayer && overworldSession
                && configsEvent && neiLoaded && neiEnabled
                && neiWorldReady
                && itemListFinished && itemCount > 0 && !registered.handlers.isEmpty();
        if (!ready) {
            resetReadinessStability();
            logReadiness(minecraft, worldReady, playerReady, creativePlayer, overworldSession,
                    configsEvent, neiLoaded, neiEnabled,
                    neiWorldReady, neiWorldPath, itemListFinished, itemCount, registered);
            if (readinessTicks >= activeRequest.readinessTimeoutTicks) {
                failBeforeStart("HANDLER_UNLOADED: readiness timed out; "
                        + readinessState(worldReady, playerReady, creativePlayer, overworldSession,
                        configsEvent, neiLoaded, neiEnabled,
                        neiWorldReady, neiWorldPath, itemListFinished, itemCount, registered));
            }
            return;
        }

        // The client world/player and NEI world-path handshake above proves the
        // integrated session is fully established. Audit the authoritative
        // server entry now; WorldClient's seed is not authoritative in 1.7.10.
        try {
            AutomationWorldBootstrap.requireLiveServerWorld(minecraft);
            if (!liveServerWorldValidated) {
                liveServerWorldValidated = true;
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Validated authoritative integrated-server "
                                + "worldServers[0]: dimension=0, seed={}, exact requested WorldInfo",
                        AutomationWorldBootstrap.WORLD_SEED);
            }
        } catch (IOException error) {
            failBeforeStart("HANDLER_UNLOADED: authoritative integrated-server world audit failed: "
                    + error.getMessage());
            return;
        }

        final String fingerprint;
        final ThaumcraftRecipeVisibilityPolicy.Snapshot visibility;
        final CompleteCategoryAdapters.RuntimeReadiness adapterReadiness;
        try {
            // Discovery fingerprints are only meaningful for the exact pinned
            // external bytecode/config corpus. Verify it before either adapter
            // reads semantic state or starts exporter-owned recomputation.
            if (!runtimePinsValidated) {
                PinnedRuntimePolicy.verify();
                runtimePinsValidated = true;
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Verified pinned adapter runtime before semantic discovery");
            }
            if (!sameHandlerIdentities(
                    structurallyValidatedHandlers, registered.handlers)) {
                HandlerCategoryPlan.validatePinnedStructuralContracts(registered.handlers);
                structurallyValidatedHandlers =
                        new ArrayList<ICraftingHandler>(registered.handlers);
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Structural preflight classified all {} registered "
                                + "crafting handlers before semantic adapter discovery",
                        registered.handlers.size());
            }
            fingerprint = handlerFingerprint(registered.handlers);
            visibility = ThaumcraftRecipeVisibilityPolicy.capture();
            adapterReadiness = CompleteCategoryAdapters.inspectPinnedRuntime(registered.handlers);
        } catch (ExportFailure failure) {
            failBeforeStart(failure.getMessage());
            return;
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            failBeforeStart("HANDLER_AMBIGUOUS: could not fingerprint registered handlers: " + error);
            return;
        }
        if (!adapterReadiness.ready) {
            resetReadinessStability();
            if (readinessTicks % 200 == 1) {
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Waiting for exact complete-category adapters ({}/{}): {}",
                        readinessTicks, activeRequest.readinessTimeoutTicks,
                        adapterReadiness.state);
            }
            if (readinessTicks >= activeRequest.readinessTimeoutTicks) {
                failBeforeStart("HANDLER_UNLOADED: complete-category adapter readiness timed out; "
                        + adapterReadiness.state);
            }
            return;
        }
        if (world == previousWorld && player == previousPlayer && items == previousItems
                && itemCount == previousItemCount && fingerprint.equals(previousFingerprint)
                && visibility.fingerprint.equals(previousVisibilityFingerprint)
                && adapterReadiness.fingerprint.equals(previousAdapterFingerprint)) {
            stableTicks++;
        } else {
            previousWorld = world;
            previousPlayer = player;
            previousItems = items;
            previousItemCount = itemCount;
            previousFingerprint = fingerprint;
            previousVisibilityFingerprint = visibility.fingerprint;
            previousAdapterFingerprint = adapterReadiness.fingerprint;
            stableTicks = 1;
        }
        if (stableTicks < activeRequest.handlerStableTicks) {
            logReadiness(minecraft, true, true, true, true, true, true, true,
                    true, EXPECTED_NEI_WORLD_PATH, true, itemCount, registered);
            if (readinessTicks >= activeRequest.readinessTimeoutTicks) {
                failBeforeStart("HANDLER_UNLOADED: active world, ItemList, and handlers did not "
                        + "stabilize before timeout; stableTicks=" + stableTicks + "/"
                        + activeRequest.handlerStableTicks);
            }
            return;
        }

        try {
            HandlerCategoryPlan.runPinnedPreExportAudits(
                    new ArrayList<ICraftingHandler>(registered.handlers));
            exportWorld = world;
            exportPlayer = player;
            exportItems = items;
            exportItemCount = itemCount;
            exportHandlerFingerprint = fingerprint;
            exportVisibilityFingerprint = visibility.fingerprint;
            exportAdapterFingerprint = adapterReadiness.fingerprint;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Readiness stable for {} ticks; world={}, items={}, "
                            + "parallelHandlers={}, serialHandlers={}, ThaumcraftVisibility={}, adapters={}",
                    stableTicks, AutomationWorldBootstrap.WORLD_FOLDER, itemCount,
                    registered.parallelCount, registered.serialCount,
                    visibility.registrySummary(), adapterReadiness.state);
            activeJob = new ExportJob(activeRequest, registered.handlers,
                    new ExportJob.RuntimeIntegrityGate() {
                        @Override
                        public void verify() throws Exception {
                            ExportFailure drift = ExportCoordinator.this.activeRuntimeDrift(true);
                            if (drift != null) {
                                throw drift;
                            }
                        }
                    });
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            failBeforeStart("HANDLER_UNLOADED: could not initialize export: " + error);
        }
    }

    private static boolean sameHandlerIdentities(
            List<ICraftingHandler> left, List<ICraftingHandler> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) {
                return false;
            }
        }
        return true;
    }

    private void bootstrapAutomationWorldIfNeeded(Minecraft minecraft) throws IOException {
        if (minecraft.theWorld != null || minecraft.thePlayer != null) {
            validateActiveAutomationSession(minecraft);
            return;
        }
        if (minecraft.isIntegratedServerRunning()) {
            validateActiveAutomationSession(minecraft);
            return;
        }
        if (automationWorldLaunchRequested) {
            return;
        }
        if (!activeRequest.bootstrapIntegratedWorld) {
            throw new IOException("request did not authorize bootstrapIntegratedWorld=true");
        }

        AutomationWorldBootstrap.PreparedWorld prepared = AutomationWorldBootstrap.prepare(
                minecraft.mcDataDir.toPath());
        if (!prepared.created) {
            AutomationWorldBootstrap.validatePersistedSettings(minecraft);
        }
        automationWorldLaunchRequested = true;
        String screen = minecraft.currentScreen == null
                ? "<none>" : minecraft.currentScreen.getClass().getName();
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] {} owned deterministic creative-superflat world {}; "
                        + "launching integrated server from screen {}",
                prepared.created ? "Created" : "Validated and reused", prepared.directory, screen);
        AutomationWorldBootstrap.launch(minecraft);
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Integrated-server launch returned; waiting for active world/player handshake");
    }

    private void validateActiveAutomationSession(Minecraft minecraft) throws IOException {
        if (ownedAutomationSessionValidated) {
            AutomationWorldBootstrap.requireActiveSessionIdentity(minecraft);
            return;
        }
        Path validated = AutomationWorldBootstrap.requireActiveOwnedWorld(minecraft);
        ownedAutomationSessionValidated = true;
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Validated active integrated session, persisted world settings, "
                        + "and exact ownership marker: {}", validated);
    }

    private void logReadiness(Minecraft minecraft, boolean worldReady, boolean playerReady,
                              boolean creativePlayer, boolean overworldSession,
                              boolean configsEvent, boolean neiLoaded, boolean neiEnabled,
                              boolean neiWorldReady, String neiWorldPath,
                              boolean itemListFinished, int itemCount, HandlerSnapshot handlers) {
        if (readinessTicks % 200 != 1) {
            return;
        }
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Waiting for runtime readiness ({}/{}): {}; "
                        + "integratedServer={}, stableTicks={}/{}",
                readinessTicks, activeRequest.readinessTimeoutTicks,
                readinessState(worldReady, playerReady, creativePlayer, overworldSession,
                        configsEvent, neiLoaded, neiEnabled,
                        neiWorldReady, neiWorldPath, itemListFinished, itemCount, handlers),
                minecraft.isIntegratedServerRunning(), stableTicks,
                activeRequest.handlerStableTicks);
    }

    private static String readinessState(boolean worldReady, boolean playerReady,
                                         boolean creativePlayer, boolean overworldSession,
                                         boolean configsEvent, boolean neiLoaded,
                                         boolean neiEnabled, boolean neiWorldReady,
                                         String neiWorldPath, boolean itemListFinished,
                                         int itemCount, HandlerSnapshot handlers) {
        return "world=" + worldReady
                + ", player=" + playerReady
                + ", creativePlayer=" + creativePlayer
                + ", overworldSession=" + overworldSession
                + ", configsEvent=" + configsEvent
                + ", neiLoaded=" + neiLoaded
                + ", neiEnabled=" + neiEnabled
                + ", neiWorldReady=" + neiWorldReady
                + ", neiWorldPath=" + neiWorldPath
                + ", itemListFinished=" + itemListFinished
                + ", items=" + itemCount
                + ", parallelHandlers=" + handlers.parallelCount
                + ", serialHandlers=" + handlers.serialCount;
    }

    private void resetReadinessStability() {
        stableTicks = 0;
        previousFingerprint = null;
        previousVisibilityFingerprint = null;
        previousAdapterFingerprint = null;
        previousWorld = null;
        previousPlayer = null;
        previousItems = null;
        previousItemCount = -1;
    }

    private static HandlerSnapshot snapshotRegisteredHandlers() {
        List<ICraftingHandler> handlers = new ArrayList<ICraftingHandler>();
        int parallelCount = 0;
        int serialCount = 0;
        if (GuiCraftingRecipe.craftinghandlers != null) {
            synchronized (GuiCraftingRecipe.craftinghandlers) {
                parallelCount = GuiCraftingRecipe.craftinghandlers.size();
                handlers.addAll(GuiCraftingRecipe.craftinghandlers);
            }
        }
        if (GuiCraftingRecipe.serialCraftingHandlers != null) {
            synchronized (GuiCraftingRecipe.serialCraftingHandlers) {
                serialCount = GuiCraftingRecipe.serialCraftingHandlers.size();
                handlers.addAll(GuiCraftingRecipe.serialCraftingHandlers);
            }
        }
        return new HandlerSnapshot(handlers, parallelCount, serialCount);
    }

    private static String handlerFingerprint(List<ICraftingHandler> handlers) {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(handlers.size()).append('|');
        for (ICraftingHandler handler : handlers) {
            if (handler == null) {
                fingerprint.append("<null>");
            } else {
                fingerprint.append(handler.getClass().getName()).append(':')
                        .append(handler.getHandlerId());
            }
            fingerprint.append('|');
        }
        return Naming.sha256(fingerprint.toString());
    }

    private void runJob() {
        if (!activeJob.isComplete()) {
            ExportFailure drift;
            try {
                drift = activeRuntimeDrift(false);
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                drift = new ExportFailure("HANDLER_UNLOADED",
                        "runtime integrity audit threw unexpectedly", error);
            }
            if (drift == null) {
                long deadline = System.nanoTime() + activeRequest.maxMillisPerTick * 1_000_000L;
                activeJob.tick(deadline);
            } else {
                activeJob.abort(drift);
            }
        }
        if (ticks % 200L == 0L && !activeJob.isComplete()) {
            GtnhNeiExportMod.LOGGER.info("[gtnh-nei-export] Progress: {}", activeJob.progress());
        }
        if (!activeJob.isComplete()) {
            return;
        }
        boolean success = !activeJob.isFailed();
        if (success) {
            try {
                ManifestContract.validatePublished(activeJob.output().resolve("manifest.json"));
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                success = false;
                GtnhNeiExportMod.LOGGER.error(
                        "[gtnh-nei-export] Published manifest validation failed", error);
            }
        }
        finishMarker(activeRequest.runningMarker, success);
        if (activeRequest.exitOnComplete) {
            scheduleExit(!success);
        }
        activeJob = null;
        activeRequest = null;
        clearExportRuntime();
    }

    private ExportFailure activeRuntimeDrift(boolean fullAudit) throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld != exportWorld || minecraft.thePlayer != exportPlayer
                || exportWorld == null || exportPlayer == null) {
            return new ExportFailure("HANDLER_UNLOADED",
                    "automation world/player changed or unloaded during export");
        }
        AutomationWorldBootstrap.requireActiveSessionIdentity(minecraft);
        if (minecraft.playerController == null
                || !minecraft.playerController.isInCreativeMode()) {
            return new ExportFailure("HANDLER_UNLOADED",
                    "owned automation player left creative mode during export");
        }
        if (exportWorld.provider == null || exportWorld.provider.dimensionId != 0
                || exportPlayer.dimension != 0) {
            return new ExportFailure("HANDLER_UNLOADED",
                    "owned automation session left dimension 0 during export; worldDimension="
                            + (exportWorld.provider == null
                            ? "null" : exportWorld.provider.dimensionId)
                            + ", playerDimension=" + exportPlayer.dimension);
        }
        String upstreamOmission = GtnhNeiExportMod.NEI_FAILURE_MONITOR.failureSummary();
        if (upstreamOmission != null) {
            return new ExportFailure("HANDLER_UNLOADED", upstreamOmission);
        }
        List<ItemStack> currentItems = ItemList.items;
        if (!ItemList.loadFinished || currentItems != exportItems
                || currentItems == null || currentItems.size() != exportItemCount) {
            return new ExportFailure("HANDLER_UNLOADED",
                    "NEI ItemList changed or became incomplete during export; expected reference/count="
                            + exportItemCount + ", actual="
                            + (currentItems == null ? "null" : currentItems.size())
                            + ", loadFinished=" + ItemList.loadFinished);
        }
        boolean neiLoaded = NEIClientConfig.isLoaded();
        boolean neiEnabled = neiLoaded && NEIClientConfig.isEnabled();
        String neiWorldPath = NEIClientConfig.getWorldPath();
        if (!neiConfigsLoadedObserved || !neiLoaded || !neiEnabled
                || !EXPECTED_NEI_WORLD_PATH.equals(neiWorldPath)) {
            return new ExportFailure("HANDLER_UNLOADED",
                    "NEI lifecycle changed during export; configsEvent="
                            + neiConfigsLoadedObserved + ", loaded=" + neiLoaded
                            + ", enabled=" + neiEnabled
                            + ", worldPath=" + neiWorldPath);
        }
        if (fullAudit || ticks % 200L == 0L) {
            AutomationWorldBootstrap.requireActiveOwnedWorld(minecraft);
            try {
                AutomationWorldBootstrap.requireLiveServerWorld(minecraft);
            } catch (IOException error) {
                return new ExportFailure("HANDLER_UNLOADED",
                        "authoritative integrated-server world audit failed during export: "
                                + error.getMessage(), error);
            }
            try {
                ThaumcraftRecipeVisibilityPolicy.Snapshot currentVisibility =
                        ThaumcraftRecipeVisibilityPolicy.capture();
                if (!currentVisibility.fingerprint.equals(exportVisibilityFingerprint)) {
                    return new ExportFailure(
                            ThaumcraftRecipeVisibilityPolicy.FAILURE_CODE,
                            "Thaumcraft recipe-visibility registry/config fingerprint changed "
                                    + "during export; " + currentVisibility.registrySummary());
                }
                HandlerSnapshot currentHandlers = snapshotRegisteredHandlers();
                String currentFingerprint = handlerFingerprint(currentHandlers.handlers);
                if (!currentFingerprint.equals(exportHandlerFingerprint)) {
                    return new ExportFailure("HANDLER_UNLOADED",
                            "NEI crafting-handler registry changed during export; parallel="
                            + currentHandlers.parallelCount + ", serial="
                                    + currentHandlers.serialCount);
                }
                CompleteCategoryAdapters.RuntimeReadiness currentAdapters = fullAudit
                        ? CompleteCategoryAdapters.auditPinnedRuntime(currentHandlers.handlers)
                        : CompleteCategoryAdapters.inspectPinnedRuntime(currentHandlers.handlers);
                if (!currentAdapters.ready
                        || !currentAdapters.fingerprint.equals(exportAdapterFingerprint)) {
                    return new ExportFailure("HANDLER_UNLOADED",
                            "complete-category adapter state changed during export; state="
                                    + currentAdapters.state);
                }
            } catch (ExportFailure failure) {
                return failure;
            } catch (Throwable error) {
                FatalErrors.rethrowIfFatal(error);
                return new ExportFailure("HANDLER_AMBIGUOUS",
                        "could not audit crafting-handler stability during export", error);
            }
        }
        return null;
    }

    private void clearExportRuntime() {
        exportWorld = null;
        exportPlayer = null;
        exportItems = null;
        exportItemCount = 0;
        exportHandlerFingerprint = null;
        exportVisibilityFingerprint = null;
        exportAdapterFingerprint = null;
    }

    private static final class HandlerSnapshot {
        final List<ICraftingHandler> handlers;
        final int parallelCount;
        final int serialCount;

        HandlerSnapshot(List<ICraftingHandler> handlers, int parallelCount, int serialCount) {
            this.handlers = handlers;
            this.parallelCount = parallelCount;
            this.serialCount = serialCount;
        }
    }

    private void failBeforeStart(String reason) {
        GtnhNeiExportMod.LOGGER.error("[gtnh-nei-export] {}", reason);
        finishMarker(activeRequest.runningMarker, false);
        if (activeRequest.exitOnComplete) {
            scheduleExit(true);
        }
        activeRequest = null;
        clearExportRuntime();
    }

    private void scheduleExit(boolean failure) {
        exitFailure = failure;
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            exitRenderState = SupervisedShutdownPolicy.capture(
                    minecraft.theWorld,
                    minecraft.thePlayer,
                    minecraft.isIntegratedServerRunning(),
                    minecraft.getIntegratedServer(),
                    DreamCoreMod.showConfirmExitWindow);
            exitCountdown = 40;
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Scheduled supervised game-loop exit in {} client ticks; "
                            + "render-critical state must remain unchanged ({})",
                    exitCountdown, exitRenderState.describe());
        } catch (Throwable error) {
            FatalErrors.rethrowIfFatal(error);
            exitRenderState = null;
            exitCountdown = -1;
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Refusing to schedule one-shot game-loop exit because "
                            + "the current render-critical state is incoherent. No direct "
                            + "cleanup or process-exit fallback will run; the supervisor will "
                            + "fail the run when its terminal-marker grace period expires.",
                    error);
        }
    }

    private static void finishMarker(Path running, boolean success) {
        if (running == null || !Files.exists(running)) {
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Cannot write {} marker; running request is absent: {}",
                    success ? "complete" : "failed", running);
            return;
        }
        String runningName = running.getFileName().toString();
        int marker = runningName.indexOf(".running-");
        String stem = marker < 0 ? runningName : runningName.substring(0, marker);
        Path destination = running.resolveSibling(stem + (success ? ".complete.json" : ".failed.json"));
        try {
            if (Files.exists(destination)) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Replacing previous automation marker {}", destination);
            }
            moveAtomicWithLoggedFallback(running, destination, true, "finish request marker");
            GtnhNeiExportMod.LOGGER.info(
                    "[gtnh-nei-export] Wrote {} marker {}", success ? "complete" : "failed", destination);
        } catch (IOException error) {
            GtnhNeiExportMod.LOGGER.error(
                    "[gtnh-nei-export] Could not finish automation marker {}", destination, error);
        }
    }

    private static void moveAtomicWithLoggedFallback(Path source, Path destination,
                                                     boolean replace, String operation) throws IOException {
        StandardCopyOption[] atomicOptions = replace
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        StandardCopyOption[] fallbackOptions = replace
                ? new StandardCopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[0];
        try {
            Files.move(source, destination, atomicOptions);
        } catch (AtomicMoveNotSupportedException unsupported) {
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Atomic move unavailable while {}; using logged fallback: {}",
                    operation, unsupported.toString());
            Files.move(source, destination, fallbackOptions);
        }
    }
}
