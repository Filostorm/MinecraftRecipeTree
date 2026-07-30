package com.recipetree.jeiexport112;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.ingredients.IIngredientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.recipetree.jeiexport112.compat.TaaccAspectSubtypeGuard;
import com.recipetree.jeiexport112.compat.TinkersComplementFluidBlacklistGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

final class ExportCoordinator {
    private static final int VIEWER_STABILIZATION_TICKS = 40;

    private final Deque<ExportRequest> queued = new ArrayDeque<ExportRequest>();
    private ExportRequest activeRequest;
    private ExportJob activeJob;
    private long ticks;
    private int requestTicks;
    private int heiReadyTicks;
    private int worldReadyTicks;
    private boolean createWorldAttempted;
    private boolean systemAutoChecked;
    private int exitCountdown = -1;

    synchronized void enqueue(ExportRequest request, String source) {
        if (queued.size() >= 16) {
            JeiExportMod.LOGGER.error("[jeiexport] Refusing {} request because 16 requests are already queued", source);
            return;
        }
        queued.addLast(request);
        JeiExportMod.LOGGER.info("[jeiexport] Queued export from {}: pack='{}', packVersion={}, " +
                        "identitySource={}, output={}, requireWorld={}, createWorld={}, exitOnComplete={}",
                source, request.pack.name,
                request.pack.version == null ? "(not supplied)" : "'" + request.pack.version + "'",
                request.pack.source, request.output, request.requireWorld, request.createWorld,
                request.exitOnComplete);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        tick();
    }

    private void tick() {
        ticks++;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (exitCountdown >= 0 && --exitCountdown <= 0) {
            JeiExportMod.LOGGER.info("[jeiexport] exitOnComplete requested; shutting down Minecraft");
            minecraft.shutdown();
            exitCountdown = -1;
            return;
        }

        if (!systemAutoChecked) {
            systemAutoChecked = true;
            final boolean auto;
            try {
                auto = StrictBooleanProperty.read("jeiexport.auto", false);
            } catch (IllegalStateException invalid) {
                JeiExportMod.LOGGER.error(
                        "[jeiexport] Automatic export configuration is invalid; no JVM one-shot " +
                                "request will be queued", invalid);
                return;
            }
            if (auto) {
                try {
                    enqueue(ExportRequest.fromSystemProperties(minecraft), "JVM one-shot properties");
                } catch (IOException e) {
                    JeiExportMod.LOGGER.error("[jeiexport] Invalid JVM one-shot request", e);
                }
            }
        }

        if (ticks % 20L == 1L) {
            pollRequestFile(minecraft);
        }

        if (activeJob != null) {
            runActiveJob();
            return;
        }
        if (activeRequest == null) {
            synchronized (this) {
                activeRequest = queued.pollFirst();
            }
            if (activeRequest == null) {
                return;
            }
            requestTicks = 0;
            heiReadyTicks = 0;
            worldReadyTicks = 0;
            createWorldAttempted = false;
        }

        waitThenStart(minecraft);
    }

    private void waitThenStart(Minecraft minecraft) {
        requestTicks++;
        IJeiRuntime runtime = JeiExportPlugin.getRuntime();
        IIngredientRegistry ingredients = JeiExportPlugin.getIngredientRegistry();
        if (runtime == null || ingredients == null) {
            if (requestTicks % 200 == 1) {
                JeiExportMod.LOGGER.info("[jeiexport] Waiting for JEI/HEI runtime and ingredient registry ({}/{} ticks)",
                        requestTicks, activeRequest.worldTimeoutTicks);
            }
            if (requestTicks >= activeRequest.worldTimeoutTicks) {
                failBeforeStart("Timed out waiting for JEI/HEI initialization");
            }
            return;
        }
        heiReadyTicks++;

        if (activeRequest.requireWorld) {
            if (minecraft.world == null || minecraft.player == null) {
                worldReadyTicks = 0;
                if (activeRequest.createWorld && !createWorldAttempted
                        && heiReadyTicks >= VIEWER_STABILIZATION_TICKS) {
                    createWorldAttempted = true;
                    try {
                        launchAutomationWorld(minecraft, activeRequest);
                    } catch (Throwable throwable) {
                        FatalErrors.rethrowIfFatal(throwable);
                        failBeforeStart("Could not create/load automation world: " + throwable);
                        return;
                    }
                }
                if (requestTicks % 200 == 1) {
                    String screen = minecraft.currentScreen == null
                            ? "none" : minecraft.currentScreen.getClass().getName();
                    JeiExportMod.LOGGER.info(
                            "[jeiexport] requireWorld=true: waiting for player/world; createWorld={}, attempted={}, screen={} " +
                                    "({}/{} ticks). No title-screen fallback will be used.",
                            activeRequest.createWorld, createWorldAttempted, screen,
                            requestTicks, activeRequest.worldTimeoutTicks);
                }
                if (requestTicks >= activeRequest.worldTimeoutTicks) {
                    failBeforeStart("Timed out waiting for required player/world; title-screen fallback is disabled");
                }
                return;
            }
            worldReadyTicks++;
            if (worldReadyTicks == 1) {
                JeiExportMod.LOGGER.info(
                        "[jeiexport] Player/world ready. Waiting {} additional ticks for stage/research synchronization.",
                        activeRequest.waitAfterWorldTicks);
            }
            if (worldReadyTicks < activeRequest.waitAfterWorldTicks) {
                if (requestTicks >= activeRequest.worldTimeoutTicks) {
                    failBeforeStart("Timed out during post-world stage synchronization wait");
                }
                return;
            }
        } else if (heiReadyTicks < VIEWER_STABILIZATION_TICKS) {
            return;
        }

        try {
            TaaccAspectSubtypeGuard.assertReadyForExport();
            TinkersComplementFluidBlacklistGuard.assertReadyForExport();
            int categoryCount = runtime.getRecipeRegistry().getRecipeCategories().size();
            int ingredientTypeCount = ingredients.getRegisteredIngredientTypes().size();
            ModContainer viewer = Loader.instance().getIndexedModList().get("jei");
            String viewerName = viewer == null ? "<missing mod container>" : viewer.getName();
            String viewerVersion = viewer == null ? "<unknown>" : viewer.getVersion();
            String viewerSource = viewer == null || viewer.getSource() == null
                    ? "<unknown>" : viewer.getSource().getName();
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Starting after readiness gate: viewer='{}' version={} source={}, " +
                            "world={}, player={}, categories={}, ingredient types={}",
                    viewerName, viewerVersion, viewerSource,
                    minecraft.world != null, minecraft.player != null,
                    categoryCount, ingredientTypeCount);
            activeJob = new ExportJob(activeRequest, runtime, ingredients);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            failBeforeStart("Could not initialize export job: " + throwable);
        }
    }

    private void runActiveJob() {
        long deadline = System.nanoTime() + activeRequest.maxMillisPerTick * 1_000_000L;
        try {
            activeJob.tick(deadline);
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            activeJob.abort(throwable);
        }
        if (ticks % 200L == 0L && !activeJob.isComplete()) {
            JeiExportMod.LOGGER.info("[jeiexport] Progress: {}", activeJob.progress());
        }
        if (activeJob.isComplete()) {
            boolean success = !activeJob.isFailed();
            finishMarker(activeRequest, success, success ? null : "Export aborted; inspect Minecraft log and staging output");
            if (activeRequest.exitOnComplete) {
                exitCountdown = 40;
            }
            activeJob = null;
            activeRequest = null;
        }
    }

    private static void launchAutomationWorld(Minecraft minecraft, ExportRequest request) {
        String screen = minecraft.currentScreen == null ? "none" : minecraft.currentScreen.getClass().getName();
        if (minecraft.isIntegratedServerRunning()) {
            throw new IllegalStateException("An integrated server is already running without a client world");
        }
        boolean recognizedTitle = minecraft.currentScreen instanceof GuiMainMenu ||
                screen.toLowerCase(java.util.Locale.ROOT).contains("mainmenu");
        if (!recognizedTitle) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] createWorld is launching from an unrecognized modded title screen class {}. " +
                            "JEI/HEI is ready and no world/server exists, so launch is still explicit and intentional.",
                    screen);
        }
        WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT);
        settings.enableCommands();
        JeiExportMod.LOGGER.info("[jeiexport] createWorld=true: launching flat creative save folder '{}' as '{}'",
                request.worldFolder, request.worldName);
        minecraft.launchIntegratedServer(request.worldFolder, request.worldName, settings);
    }

    private void pollRequestFile(Minecraft minecraft) {
        Path requestFile = minecraft.gameDir.toPath().resolve("jeiexport-request.json");
        if (!Files.exists(requestFile, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path running = requestFile.resolveSibling("jeiexport-request.running-" + System.currentTimeMillis() + ".json");
        try {
            moveWithLoggedAtomicFallback(requestFile, running, "consume request file");
            ExportRequest request = ExportRequest.fromFile(running, minecraft);
            request.runningMarker = running;
            enqueue(request, "request file " + running.getFileName());
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            JeiExportMod.LOGGER.error("[jeiexport] Invalid request file {}; it will not be retried", running, throwable);
            ExportRequest markerOnly;
            try {
                markerOnly = ExportRequest.fromCommand(null, minecraft);
                markerOnly.runningMarker = Files.exists(running) ? running : requestFile;
                finishMarker(markerOnly, false, "Invalid request: " + throwable);
            } catch (IOException markerFailure) {
                JeiExportMod.LOGGER.error("[jeiexport] Could not mark invalid request as failed", markerFailure);
            }
        }
    }

    private void failBeforeStart(String reason) {
        JeiExportMod.LOGGER.error("[jeiexport] Request failed before export start: {}", reason);
        finishMarker(activeRequest, false, reason);
        if (activeRequest.exitOnComplete) {
            exitCountdown = 40;
        }
        activeRequest = null;
    }

    private static void finishMarker(ExportRequest request, boolean success, String error) {
        Path running = request.runningMarker;
        if (running == null || !Files.exists(running)) {
            return;
        }
        Path marker = running.resolveSibling(running.getFileName() + (success ? ".done" : ".failed"));
        try {
            moveWithLoggedAtomicFallback(running, marker, "finish one-shot request marker");
            if (error != null) {
                Files.write(marker.resolveSibling(marker.getFileName() + ".error.txt"),
                        Collections.singletonList(error), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            JeiExportMod.LOGGER.error("[jeiexport] Could not finalize request marker {}", running, e);
        }
    }

    private static void moveWithLoggedAtomicFallback(Path source, Path destination, String operation)
            throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            JeiExportMod.LOGGER.warn("[jeiexport] Atomic move unavailable while {}; using non-atomic move: {}",
                    operation, unsupported.toString());
            Files.move(source, destination);
        }
    }
}
