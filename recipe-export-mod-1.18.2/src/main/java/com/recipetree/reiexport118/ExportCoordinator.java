package com.recipetree.reiexport118;

import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyCompatibility;
import com.recipetree.reiexport118.compat.Mm2RegistryRepairs;
import com.recipetree.reiexport118.compat.Mm2ReiLifecycleGate;
import com.recipetree.reiexport118.compat.Mm2LightmapReadiness;
import com.recipetree.reiexport118.compat.Mm2UnattendedUiScope;
import com.recipetree.reiexport118.compat.RelicsStatRandomDeterminism;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class ExportCoordinator {
    private static final int REQUIRED_STABLE_TICKS = 200;

    record Claim(ExportRequest request, Path runningMarker, long timestamp) {
    }

    private static RegistryCensus.Capture stableCapture;
    private static int stableTicks;
    private static ExportJob current;

    private ExportCoordinator() {
    }

    static void tick() {
        if (current != null) {
            current.tick();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Path gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        Path requestPath = gameDirectory.resolve(ExportRequest.ACTIVE_NAME);
        if (!Files.isRegularFile(requestPath) || minecraft.level == null) {
            resetReadiness(false);
            releaseUnclaimedScopes("request absent before atomic claim");
            return;
        }
        try {
            Mm2UnattendedUiScope.armForExactRequest(gameDirectory);
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 unattended UI scope could not arm; no focus-state fallback was attempted",
                    throwable);
            resetReadiness(false);
            failActiveOrUnclaimed(requestPath, throwable);
            return;
        }
        try {
            Mm2ReiLifecycleGate.requireCompleteForExport();
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 REI lifecycle gate rejected the active request; no fallback was attempted",
                    throwable);
            resetReadiness(false);
            failActiveOrUnclaimed(requestPath, throwable);
            return;
        }
        try {
            if (!Mm2LightmapReadiness.pollReadyBeforeClaim()) {
                resetReadiness(true);
                return;
            }
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 lightmap readiness rejected the active request; no "
                            + "synthetic update or pixel fallback was attempted",
                    throwable);
            resetReadiness(false);
            failActiveOrUnclaimed(requestPath, throwable);
            return;
        }
        if (PluginManager.areAnyReloading()) {
            resetReadiness(true);
            return;
        }
        RegistryCensus.Counts snapshot;
        try {
            snapshot = RegistryCensus.captureCounts();
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] REI registry census was not internally consistent; readiness is restarting and no fallback was attempted",
                    throwable);
            resetReadiness(false);
            return;
        }
        if (snapshot.entries() <= 0 || snapshot.displays() <= 0 || snapshot.categories() <= 0) {
            resetReadiness(true);
            return;
        }
        if (stableCapture == null || !snapshot.equals(stableCapture.contract().counts())) {
            if (stableCapture != null) {
                ReiExportMod.LOGGER.warn("[reiexport] REI registry counts changed during readiness: {} -> {}; restarting the stability gate",
                        stableCapture.contract().counts().summary(), snapshot.summary());
            }
            Mm2RegistryRepairs.SettlementResult settlement;
            try {
                settlement = Mm2RegistryRepairs.canonicalizeSettledEntries(
                        Mm2RegistryRepairs.SettlementSeam.READINESS_CANDIDATE);
            } catch (Throwable throwable) {
                failSettledCanonicalization(
                        requestPath,
                        Mm2RegistryRepairs.SettlementSeam.READINESS_CANDIDATE,
                        throwable);
                return;
            }
            try {
                snapshot = RegistryCensus.captureCounts();
                if (snapshot.entries() <= 0
                        || snapshot.displays() <= 0
                        || snapshot.categories() <= 0) {
                    throw new IllegalStateException(
                            "REI registry became empty after MM2 readiness canonicalization: "
                                    + snapshot.summary());
                }
                RegistryCensus.Capture candidate = RegistryCensus.captureDeepWithDiagnostics();
                if (!snapshot.equals(candidate.contract().counts())) {
                    throw new IllegalStateException(
                            "REI registry changed between the count and deep candidate censuses: "
                                    + snapshot.summary() + " -> " + candidate.contract().counts().summary());
                }
                stableCapture = candidate;
                stableTicks = 1;
                ReiExportMod.LOGGER.info(
                        "[reiexport] REI readiness candidate census: {}",
                        candidate.contract().summary());
                if (settlement.changed()) {
                    ReiExportMod.LOGGER.warn(
                            "[reiexport] MM2 settled entry canonicalization changed the registry "
                                    + "before the deep readiness candidate; the stability gate "
                                    + "began from the canonical census mutation={}",
                            settlement);
                }
            } catch (RegistryCensus.NullEntryStackException exception) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] REI exposed a null registry entry; the active request is being failed and no retry or fallback was attempted",
                        exception);
                resetReadiness(false);
                failActiveOrUnclaimed(requestPath, exception);
            } catch (Throwable throwable) {
                ReiExportMod.LOGGER.warn(
                        "[reiexport] REI deep candidate census was unstable; readiness is restarting and no fallback was attempted",
                        throwable);
                resetReadiness(false);
            }
            return;
        }
        stableTicks++;
        if (stableTicks < REQUIRED_STABLE_TICKS) {
            return;
        }
        Mm2RegistryRepairs.SettlementResult preClaimSettlement;
        try {
            preClaimSettlement = Mm2RegistryRepairs.canonicalizeSettledEntries(
                    Mm2RegistryRepairs.SettlementSeam.PRE_CLAIM);
        } catch (Throwable throwable) {
            failSettledCanonicalization(
                    requestPath,
                    Mm2RegistryRepairs.SettlementSeam.PRE_CLAIM,
                    throwable);
            return;
        }
        if (preClaimSettlement.changed()) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] MM2 settled entry canonicalization changed the registry before "
                            + "the atomic request claim; readiness is restarting from a new deep "
                            + "candidate mutation={}",
                    preClaimSettlement);
            resetReadiness(false);
            return;
        }
        Claim claim = null;
        try {
            Mm2UnattendedUiScope.requireReadyForClaim();
            Mm2LightmapReadiness.requireReadyForClaim();
            claim = claim(requestPath);
            RelicsStatRandomDeterminism.requireObservedRuntimeApplication();
            KubeJsTooltipConcurrencyCompatibility.requireHealthyIfApplicable();
            if (PluginManager.areAnyReloading()) {
                throw new IllegalStateException("REI began reloading while the export request was claimed");
            }
            RegistryCensus.Capture currentCapture = RegistryCensus.captureDeepWithDiagnostics();
            RegistryCensus.Deep currentCensus = currentCapture.contract();
            if (!stableCapture.contract().equals(currentCensus)) {
                throw new IllegalStateException("REI registry census changed while the export request was claimed: "
                        + stableCapture.contract().summary() + " -> " + currentCensus.summary());
            }
            requireExpectedRegistryCensusWithDiagnostics(
                    gameDirectory, claim.request(), currentCapture);
            RegistryCensus.Counts currentSnapshot = currentCensus.counts();
            ExportPlan plan = ExportPlan.build(
                    claim.request(), currentSnapshot.entries(), currentSnapshot.displays(),
                    currentSnapshot.categories());
            try (OffscreenRenderer calibrationRenderer = new OffscreenRenderer()) {
                calibrationRenderer.validateReadbackOrientation();
                calibrationRenderer.validateTranslucentCullBaseline();
            }
            ExportContext context = new ExportContext(gameDirectory, claim.request(), claim.timestamp());
            current = new ExportJob(claim, context, plan);
            ReiExportMod.LOGGER.info("[reiexport] Claimed {} after {} stable ticks: pack='{}' "
                            + "packVersion='{}' identitySource=explicit-request entries={} displays={} "
                            + "categories={} sample={}",
                    claim.runningMarker().getFileName(), stableTicks, claim.request().packName,
                    claim.request().packVersion, currentSnapshot.entries(), currentSnapshot.displays(),
                    currentSnapshot.categories(), claim.request().isQualitySample());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Claimed exact REI registry census: {}",
                    currentCensus.summary());
            resetReadiness(false);
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error("[reiexport] Could not start the requested export; no fallback was attempted", throwable);
            resetReadiness(false);
            if (claim != null) {
                completeFailure(claim, "Export request failed after its atomic claim; cause=" + throwable);
            } else {
                failActiveOrUnclaimed(requestPath, throwable);
            }
        }
    }

    private static void failSettledCanonicalization(
            Path requestPath,
            Mm2RegistryRepairs.SettlementSeam seam,
            Throwable failure
    ) {
        ReiExportMod.LOGGER.error(
                "[reiexport] Exact MM2 settled entry canonicalization failed at {} and the "
                        + "active request is being terminalized; no retry or fallback was attempted",
                seam, failure);
        resetReadiness(false);
        failActiveOrUnclaimed(requestPath, failure);
    }

    private static void requireExpectedRegistryCensusWithDiagnostics(
            Path gameDirectory,
            ExportRequest request,
            RegistryCensus.Capture currentCapture) throws IOException {
        IllegalStateException mismatch = null;
        try {
            request.requireExpectedRegistryCensus(currentCapture.contract());
        } catch (IllegalStateException exception) {
            mismatch = exception;
        }

        if (mismatch == null) {
            RegistryCensusDiagnostics.StoredSnapshot current =
                    RegistryCensusDiagnostics.publish(gameDirectory, currentCapture);
            ReiExportMod.LOGGER.info(
                    "[reiexport] Published exact hashed REI census diagnostics: {}",
                    current.path());
            return;
        }

        try {
            RegistryCensusDiagnostics.StoredSnapshot current =
                    RegistryCensusDiagnostics.publish(gameDirectory, currentCapture);
            ExportRequest.ExpectedRegistryCensus expected = request.expectedRegistryCensus;
            if (expected == null) {
                throw new IllegalStateException(
                        "REI registry census mismatch was reported without an expected census contract",
                        mismatch);
            }
            RegistryCensusDiagnostics.SnapshotId expectedId =
                    new RegistryCensusDiagnostics.SnapshotId(
                            expected.categoryCountsSha256(), expected.entryIdentitiesSha256());
            RegistryCensusDiagnostics.MismatchEvidence evidence =
                    RegistryCensusDiagnostics.compareExpected(
                            gameDirectory, expectedId, current);
            throw new IllegalStateException(
                    mismatch.getMessage() + "; " + evidence.summary(), mismatch);
        } catch (IOException diagnosticsFailure) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Exact registry census validation failed and its diagnostic evidence could not be published; no fallback was attempted",
                    diagnosticsFailure);
            IllegalStateException combined = new IllegalStateException(
                    mismatch.getMessage()
                            + "; registry diagnostic evidence publication failed and was logged: "
                            + diagnosticsFailure,
                    mismatch);
            combined.addSuppressed(diagnosticsFailure);
            throw combined;
        }
    }

    private static Claim claim(Path requestPath) throws IOException {
        long timestamp = System.currentTimeMillis();
        Path running = requestPath.resolveSibling("reiexport-request.running-" + timestamp + ".json");
        try {
            Files.move(requestPath, running, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic request claim is unavailable; no non-atomic fallback was attempted.", exception);
        }
        try {
            return new Claim(ExportRequest.read(running), running, timestamp);
        } catch (IOException exception) {
            terminalizeUnreadableClaim(running, exception);
            throw exception;
        } catch (RuntimeException | Error failure) {
            terminalizeUnreadableClaim(running, failure);
            throw failure;
        }
    }

    private static void terminalizeUnreadableClaim(Path running, Throwable cause) {
        Path failed = running.resolveSibling(running.getFileName() + ".failed");
        try {
            Files.move(running, failed, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(
                    failed.resolveSibling(failed.getFileName() + ".log"),
                    "Export request could not be parsed after its atomic claim; cause=" + cause + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Throwable markerFailure) {
            cause.addSuppressed(markerFailure);
            ReiExportMod.LOGGER.error("[reiexport] Could not terminalize the unreadable claimed request", markerFailure);
        } finally {
            Minecraft.getInstance().stop();
        }
    }

    static void completeSuccess(Claim claim, String message) {
        complete(claim, true, message);
    }

    static void completeFailure(Claim claim, String message) {
        complete(claim, false, message);
    }

    private static void complete(Claim claim, boolean success, String message) {
        current = null;
        boolean terminalSuccess = success;
        String terminalMessage = message;
        try {
            Mm2UnattendedUiScope.releaseIfActive(
                    "terminal safety cleanup: " + (success ? "success" : "failure"));
        } catch (Throwable throwable) {
            terminalSuccess = false;
            terminalMessage = message + "; unattended UI scope cleanup failed=" + throwable;
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 unattended UI scope terminal cleanup failed; success marker publication is rejected",
                    throwable);
        }
        Path terminal = claim.runningMarker().resolveSibling(
                claim.runningMarker().getFileName()
                        + (terminalSuccess ? ".done" : ".failed"));
        try {
            Files.move(claim.runningMarker(), terminal, StandardCopyOption.ATOMIC_MOVE);
            Files.writeString(
                    terminal.resolveSibling(terminal.getFileName() + ".log"),
                    terminalMessage + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error("[reiexport] Terminal marker publication failed", throwable);
        }
        if (claim.request().exitOnComplete) {
            Minecraft.getInstance().stop();
        }
    }

    private static void failActiveOrUnclaimed(Path requestPath, Throwable throwable) {
        boolean stopAfterFailure = true;
        try {
            Mm2UnattendedUiScope.releaseIfActive("request failed before ExportJob ownership");
        } catch (Throwable cleanupFailure) {
            throwable.addSuppressed(cleanupFailure);
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 unattended UI scope cleanup also failed",
                    cleanupFailure);
        }
        try {
            if (Files.exists(requestPath)) {
                try {
                    stopAfterFailure = ExportRequest.read(requestPath).exitOnComplete;
                } catch (Throwable requestReadFailure) {
                    throwable.addSuppressed(requestReadFailure);
                    ReiExportMod.LOGGER.error(
                            "[reiexport] Failed request could not be reread to recover its "
                                    + "exitOnComplete policy; the invalid unattended state will "
                                    + "be stopped rather than left running",
                            requestReadFailure);
                    stopAfterFailure = true;
                }
                long timestamp = System.currentTimeMillis();
                Path failed = requestPath.resolveSibling("reiexport-request.running-" + timestamp + ".json.failed");
                Files.move(requestPath, failed, StandardCopyOption.ATOMIC_MOVE);
                Files.writeString(
                        failed.resolveSibling(failed.getFileName() + ".log"),
                        throwable.toString() + System.lineSeparator(),
                        StandardCharsets.UTF_8);
            }
        } catch (Throwable markerFailure) {
            stopAfterFailure = true;
            throwable.addSuppressed(markerFailure);
            ReiExportMod.LOGGER.error("[reiexport] Failed to publish the request-start failure marker", markerFailure);
        } finally {
            if (stopAfterFailure) {
                ReiExportMod.LOGGER.info(
                        "[reiexport] Stopping Minecraft after an unclaimed terminal export "
                                + "failure because exitOnComplete=true or the request was unreadable");
                Minecraft.getInstance().stop();
            }
        }
    }

    static void failUnclaimedRequest(Throwable throwable) {
        Path requestPath = Minecraft.getInstance().gameDirectory.toPath().resolve(ExportRequest.ACTIVE_NAME);
        failActiveOrUnclaimed(requestPath, throwable);
    }

    static void releaseUnclaimedScopes(String outcome) {
        try {
            Mm2UnattendedUiScope.releaseIfActive(outcome);
        } catch (Throwable throwable) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 unattended UI scope cleanup failed while no active request was present",
                    throwable);
        }
    }

    static void abortForLogout() {
        ExportJob job = current;
        if (job != null) {
            job.abort("Client level closed while export was active");
            return;
        }
        Path requestPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve(ExportRequest.ACTIVE_NAME);
        if (Files.exists(requestPath)) {
            IllegalStateException failure = new IllegalStateException(
                    "Client level closed during MM2 readiness before the atomic request claim");
            ReiExportMod.LOGGER.error(
                    "[reiexport] Client logout interrupted MM2 readiness; the unclaimed request "
                            + "is being failed and no resume fallback was attempted",
                    failure);
            failActiveOrUnclaimed(requestPath, failure);
        } else {
            releaseUnclaimedScopes(
                    "client logout with no active ExportJob or request");
        }
    }

    private static void resetReadiness(boolean log) {
        if (log && stableTicks > 0) {
            ReiExportMod.LOGGER.warn("[reiexport] REI readiness was interrupted after {} stable ticks; restarting", stableTicks);
        }
        stableCapture = null;
        stableTicks = 0;
    }
}
