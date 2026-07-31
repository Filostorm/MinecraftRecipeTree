package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import com.recipetree.reiexport118.mixin.LightTexturePixelsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.multiplayer.ClientLevel;

import java.nio.file.Path;

/**
 * Requires a real post-level vanilla lightmap update before MM2 captures begin, then audits the
 * CPU-side full-bright texel before every capture. No random- or world-dependent update is forced.
 */
public final class Mm2LightmapReadiness {
    private static Minecraft owner;
    private static LightTexture lightTexture;
    private static Path gameDirectory;
    private static ClientLevel ownedLevel;
    private static boolean active;
    private static long completedUpdates;
    private static long postLevelUpdates;
    private static long captureChecks;
    private static int readinessWaitTicks;
    private static int lastFullBrightAbgr;
    private static boolean waitingLogged;
    private static boolean readyLogged;

    private Mm2LightmapReadiness() {
    }

    public static void armForExactRequest(Minecraft minecraft, Path requestedGameDirectory) {
        requireRenderThread("arm");
        if (minecraft == null) {
            throw new IllegalStateException("MM2 lightmap readiness received a null Minecraft");
        }
        Path normalized = requestedGameDirectory.toAbsolutePath().normalize();
        if (active) {
            if (owner != minecraft || !normalized.equals(gameDirectory)
                    || lightTexture != minecraft.gameRenderer.lightTexture()) {
                throw new IllegalStateException(
                        "MM2 lightmap readiness ownership changed while active");
            }
            return;
        }
        Mm2ExportRequestScope.Inspection request = Mm2ExportRequestScope.inspect(normalized);
        if (!request.isExactMm2()) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness cannot arm without the exact exporter request");
        }
        Path runtime = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!normalized.equals(runtime)) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness game-directory mismatch: requested=" + normalized
                            + ", runtime=" + runtime);
        }
        LightTexture current = minecraft.gameRenderer.lightTexture();
        if (current == null) {
            throw new IllegalStateException("Minecraft GameRenderer has no LightTexture");
        }
        owner = minecraft;
        lightTexture = current;
        gameDirectory = normalized;
        ownedLevel = null;
        active = true;
        completedUpdates = 0L;
        postLevelUpdates = 0L;
        captureChecks = 0L;
        readinessWaitTicks = 0;
        lastFullBrightAbgr = readFullBright(current);
        waitingLogged = false;
        readyLogged = false;
        ReiExportMod.LOGGER.info(
                "[reiexport] Armed exact MM2 lightmap readiness audit: initialFullBrightAbgr={}, "
                        + "requiredAbgr={}, syntheticUpdates=disabled",
                hex(lastFullBrightAbgr),
                hex(Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR));
    }

    /** Injection seam immediately after the actual vanilla DynamicTexture upload. */
    public static void recordCompletedVanillaUpdate(LightTexture updated) {
        if (!active) {
            return;
        }
        requireRenderThread("record a completed vanilla update");
        requireOwner("completed vanilla update");
        if (updated == null || updated != lightTexture
                || updated != owner.gameRenderer.lightTexture()) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness observed an update from an unowned LightTexture");
        }
        completedUpdates++;
        if (owner.level == null) {
            return;
        }
        if (ownedLevel == null) {
            ownedLevel = owner.level;
        } else if (owner.level != ownedLevel) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness client level changed after ownership was established");
        }
        postLevelUpdates++;
        lastFullBrightAbgr = readFullBright(updated);
    }

    /** Returns false while waiting for one normal post-level render, then fail-closes at 30s. */
    public static boolean pollReadyBeforeClaim() {
        requireActive("readiness poll");
        if (owner.level == null) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness was polled without an active client level");
        }
        if (ownedLevel != null && owner.level != ownedLevel) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness client level drifted before the atomic claim");
        }
        if (postLevelUpdates > 0L) {
            requireExpectedPixel("readiness poll");
            if (!readyLogged) {
                readyLogged = true;
                ReiExportMod.LOGGER.info(
                        "[reiexport] MM2 lightmap became capture-ready through a normal world "
                                + "render: completedUpdates={}, postLevelUpdates={}, "
                                + "fullBrightAbgr={}",
                        completedUpdates,
                        postLevelUpdates,
                        hex(lastFullBrightAbgr));
            }
            return true;
        }
        readinessWaitTicks++;
        lastFullBrightAbgr = readFullBright(lightTexture);
        if (!waitingLogged) {
            waitingLogged = true;
            ReiExportMod.LOGGER.warn(
                    "[reiexport] Waiting for a real post-level vanilla lightmap update before "
                            + "MM2 readiness can advance: observedAbgr={}, requiredAbgr={}, "
                            + "timeoutTicks={}; no synthetic update or pixel fallback will run",
                    hex(lastFullBrightAbgr),
                    hex(Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR),
                    Mm2LightmapReadinessContract.READINESS_TIMEOUT_TICKS);
        }
        if (readinessWaitTicks >= Mm2LightmapReadinessContract.READINESS_TIMEOUT_TICKS) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness timed out without a post-level vanilla update: "
                            + "waitTicks=" + readinessWaitTicks
                            + ", completedUpdates=" + completedUpdates
                            + ", observedAbgr=" + hex(lastFullBrightAbgr));
        }
        return false;
    }

    public static void requireReadyForClaim() {
        requireActive("atomic request claim");
        if (postLevelUpdates <= 0L) {
            throw new IllegalStateException(
                    "MM2 lightmap had no post-level vanilla update at the atomic request claim");
        }
        requireExpectedPixel("atomic request claim");
    }

    public static void requireCaptureBaseline(String label) {
        requireActive("native capture " + bounded(label));
        if (postLevelUpdates <= 0L) {
            throw new IllegalStateException(
                    "MM2 native capture started before a post-level vanilla lightmap update");
        }
        requireExpectedPixel("native capture " + bounded(label));
        captureChecks++;
    }

    public static void requireHealthyBeforePublication() {
        requireActive("publication audit");
        if (postLevelUpdates <= 0L || captureChecks <= 0L) {
            throw new IllegalStateException(
                    "MM2 lightmap publication audit was bypassed: postLevelUpdates="
                            + postLevelUpdates + ", captureChecks=" + captureChecks);
        }
        requireExpectedPixel("publication audit");
        ReiExportMod.LOGGER.info(
                "[reiexport] Verified exact MM2 lightmap before publication: "
                        + "completedUpdates={}, postLevelUpdates={}, captureChecks={}, "
                        + "fullBrightAbgr={}",
                completedUpdates,
                postLevelUpdates,
                captureChecks,
                hex(lastFullBrightAbgr));
    }

    public static void releaseIfActive(String outcome) {
        requireRenderThread("release");
        if (!active) {
            return;
        }
        String ownerState = owner == Minecraft.getInstance() ? "same" : "drifted";
        ReiExportMod.LOGGER.info(
                "[reiexport] Released exact MM2 lightmap readiness audit: outcome={}, "
                        + "owner={}, completedUpdates={}, postLevelUpdates={}, captureChecks={}, "
                        + "lastFullBrightAbgr={}",
                bounded(outcome),
                ownerState,
                completedUpdates,
                postLevelUpdates,
                captureChecks,
                hex(lastFullBrightAbgr));
        owner = null;
        lightTexture = null;
        gameDirectory = null;
        ownedLevel = null;
        active = false;
        completedUpdates = 0L;
        postLevelUpdates = 0L;
        captureChecks = 0L;
        readinessWaitTicks = 0;
        lastFullBrightAbgr = 0;
        waitingLogged = false;
        readyLogged = false;
        if (!"same".equals(ownerState)) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness owner drifted before release");
        }
    }

    public static boolean isActive() {
        return active;
    }

    private static void requireExpectedPixel(String operation) {
        lastFullBrightAbgr = readFullBright(lightTexture);
        if (lastFullBrightAbgr != Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR) {
            throw new IllegalStateException(
                    "MM2 lightmap full-bright texel drift during " + operation
                            + ": expected="
                            + hex(Mm2LightmapReadinessContract.EXPECTED_FULL_BRIGHT_ABGR)
                            + ", actual=" + hex(lastFullBrightAbgr));
        }
    }

    private static int readFullBright(LightTexture target) {
        if (!(target instanceof LightTexturePixelsAccessor accessor)) {
            throw new IllegalStateException(
                    "MM2 LightTexture pixel accessor mixin was not applied");
        }
        NativeImage pixels = accessor.reiexport$getLightPixels();
        if (pixels == null || pixels.getWidth() != 16 || pixels.getHeight() != 16) {
            throw new IllegalStateException(
                    "MM2 LightTexture CPU image shape drift: actual="
                            + (pixels == null ? "null"
                            : pixels.getWidth() + "x" + pixels.getHeight()));
        }
        return pixels.getPixelRGBA(
                Mm2LightmapReadinessContract.FULL_BRIGHT_X,
                Mm2LightmapReadinessContract.FULL_BRIGHT_Y);
    }

    private static void requireActive(String operation) {
        requireRenderThread(operation);
        if (!active || owner == null || lightTexture == null || gameDirectory == null) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness is not active for " + operation);
        }
        requireOwner(operation);
    }

    private static void requireOwner(String operation) {
        if (owner != Minecraft.getInstance()
                || lightTexture != owner.gameRenderer.lightTexture()
                || (ownedLevel != null && owner.level != ownedLevel)) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness ownership drifted during " + operation);
        }
    }

    private static void requireRenderThread(String operation) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException(
                    "MM2 lightmap readiness must " + operation
                            + " on Minecraft's render thread");
        }
    }

    private static String hex(int value) {
        return String.format("0x%08x", value);
    }

    private static String bounded(String value) {
        if (value == null) {
            return "<null>";
        }
        int end = Math.min(value.length(), 180);
        return value.substring(0, end) + (end < value.length() ? "..." : "");
    }
}
