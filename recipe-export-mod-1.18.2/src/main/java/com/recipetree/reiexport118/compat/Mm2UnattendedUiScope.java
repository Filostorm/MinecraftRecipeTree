package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;

/**
 * Removes window-focus state from the exact MM2 export lifecycle without persisting an option
 * change. Native off-screen rendering is permitted only while no Minecraft Screen is active.
 */
public final class Mm2UnattendedUiScope {
    public static final String VANILLA_PAUSE_SCREEN_CLASS =
            "net.minecraft.client.gui.screens.PauseScreen";

    private static Minecraft owner;
    private static Path gameDirectory;
    private static boolean originalPauseOnLostFocus;
    private static boolean active;
    private static long captureChecks;
    private static int clearedReadinessPauseScreens;

    private Mm2UnattendedUiScope() {
    }

    /** Arms once after the exact request has been validated and before its world is opened. */
    public static void armForExactRequest(Path requestedGameDirectory) {
        requireRenderThread("arm");
        Path normalized = requestedGameDirectory.toAbsolutePath().normalize();
        if (active) {
            if (!normalized.equals(gameDirectory) || owner != Minecraft.getInstance()) {
                throw new IllegalStateException(
                        "MM2 unattended UI scope ownership changed while active");
            }
            requirePauseOnLostFocusDisabled("active-scope recheck");
            Mm2LightmapReadiness.armForExactRequest(owner, normalized);
            normalizeExactPauseScreenDuringReadiness();
            return;
        }

        Mm2ExportRequestScope.Inspection request = Mm2ExportRequestScope.inspect(normalized);
        if (!request.isExactMm2()) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope cannot arm without the exact exporter request");
        }

        Minecraft minecraft = Minecraft.getInstance();
        Path runtimeGameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        if (!normalized.equals(runtimeGameDirectory)) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope game-directory mismatch: requested=" + normalized
                            + ", runtime=" + runtimeGameDirectory);
        }

        boolean previous = minecraft.options.pauseOnLostFocus;
        minecraft.options.pauseOnLostFocus = false;
        if (minecraft.options.pauseOnLostFocus) {
            minecraft.options.pauseOnLostFocus = previous;
            throw new IllegalStateException(
                    "MM2 unattended UI scope could not disable pauseOnLostFocus in memory");
        }

        owner = minecraft;
        gameDirectory = normalized;
        originalPauseOnLostFocus = previous;
        captureChecks = 0L;
        clearedReadinessPauseScreens = 0;
        active = true;
        Mm2LightmapReadiness.armForExactRequest(minecraft, normalized);
        normalizeExactPauseScreenDuringReadiness();
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact MM2 unattended UI scope before world/export readiness: "
                        + "pauseOnLostFocus {} -> false in memory only; options.txt is not written; "
                        + "every native capture requires Minecraft.screen=null",
                previous);
    }

    /**
     * Verifies that the early readiness normalization held. Any UI at the atomic claim is
     * rejected; clearing here would be too late for a normal world render to initialize lightmap.
     */
    public static void requireReadyForClaim() {
        requireActive("atomic request claim");
        requirePauseOnLostFocusDisabled("atomic request claim");
        Screen screen = owner.screen;
        if (screen != null) {
            throw new IllegalStateException(
                    "MM2 export claim requires no active Minecraft Screen; actual="
                            + screen.getClass().getName()
                            + "; no claim-time UI clearing fallback is allowed");
        }
    }

    /** Called by every exporter-owned FBO capture before any native draw occurs. */
    public static void requireCaptureBaseline(String label) {
        requireActive("native capture " + bounded(label));
        requirePauseOnLostFocusDisabled("native capture " + bounded(label));
        if (owner.screen != null) {
            throw new IllegalStateException(
                    "MM2 native capture requires Minecraft.screen=null; label=" + bounded(label)
                            + ", actual=" + owner.screen.getClass().getName());
        }
        captureChecks++;
    }

    /** Fails publication if the lifecycle or any capture check was bypassed. */
    public static void requireHealthyBeforePublication() {
        requireActive("publication audit");
        requirePauseOnLostFocusDisabled("publication audit");
        if (owner.screen != null) {
            throw new IllegalStateException(
                    "MM2 publication audit found an active Minecraft Screen: "
                            + owner.screen.getClass().getName());
        }
        if (captureChecks <= 0L) {
            throw new IllegalStateException(
                    "MM2 publication audit observed no native capture baseline checks");
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Verified exact MM2 unattended UI scope before publication: "
                        + "captureChecks={}, clearedReadinessPauseScreens={}, "
                        + "pauseOnLostFocus=false, activeScreen=null",
                captureChecks,
                clearedReadinessPauseScreens);
    }

    /** Restores the exact in-memory option before terminal marker publication and shutdown. */
    public static void releaseIfActive(String outcome) {
        requireRenderThread("release");
        if (!active) {
            Mm2LightmapReadiness.releaseIfActive(outcome);
            return;
        }
        Minecraft minecraft = owner;
        boolean expectedOriginal = originalPauseOnLostFocus;
        boolean optionDrifted = minecraft == null || minecraft.options.pauseOnLostFocus;
        String screenClass = minecraft == null || minecraft.screen == null
                ? null : minecraft.screen.getClass().getName();
        Throwable releaseFailure = null;
        try {
            Mm2LightmapReadiness.releaseIfActive(outcome);
        } catch (Throwable failure) {
            releaseFailure = failure;
        }
        try {
            if (minecraft == null || minecraft != Minecraft.getInstance()) {
                throw new IllegalStateException(
                        "MM2 unattended UI scope lost its Minecraft owner during release");
            }
            minecraft.options.pauseOnLostFocus = expectedOriginal;
            if (minecraft.options.pauseOnLostFocus != expectedOriginal) {
                throw new IllegalStateException(
                        "MM2 unattended UI scope did not restore pauseOnLostFocus="
                                + expectedOriginal);
            }
        } catch (Throwable failure) {
            if (releaseFailure == null) {
                releaseFailure = failure;
            } else {
                releaseFailure.addSuppressed(failure);
            }
        } finally {
            owner = null;
            gameDirectory = null;
            originalPauseOnLostFocus = false;
            active = false;
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Released exact MM2 unattended UI scope: outcome={}, "
                        + "restoredPauseOnLostFocus={}, captureChecks={}, "
                        + "clearedReadinessPauseScreens={}, optionDriftedBeforeRestore={}, "
                        + "screenAtRelease={}",
                bounded(outcome),
                expectedOriginal,
                captureChecks,
                clearedReadinessPauseScreens,
                optionDrifted,
                screenClass == null ? "null" : screenClass);
        captureChecks = 0L;
        clearedReadinessPauseScreens = 0;
        if (optionDrifted) {
            IllegalStateException drift = new IllegalStateException(
                    "MM2 unattended UI scope detected pauseOnLostFocus drift before release");
            if (releaseFailure == null) {
                releaseFailure = drift;
            } else {
                releaseFailure.addSuppressed(drift);
            }
        }
        if (releaseFailure != null) {
            if (releaseFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (releaseFailure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "MM2 unattended UI scope release failed", releaseFailure);
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isExactVanillaPauseScreenClassName(String className) {
        return VANILLA_PAUSE_SCREEN_CLASS.equals(className);
    }

    /** Clears only an exact PauseScreen while readiness still has time for a normal world frame. */
    private static void normalizeExactPauseScreenDuringReadiness() {
        if (owner.level == null || owner.screen == null) {
            return;
        }
        Screen screen = owner.screen;
        if (!isExactVanillaPauseScreenClassName(screen.getClass().getName())
                || screen.getClass() != PauseScreen.class) {
            return;
        }
        owner.screen = null;
        if (owner.screen != null) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope could not clear the exact readiness PauseScreen");
        }
        clearedReadinessPauseScreens++;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Cleared one exact vanilla PauseScreen during MM2 readiness, before "
                        + "the stability interval; pauseOnLostFocus is already false, direct Screen "
                        + "assignment avoids lifecycle callbacks, and every other Screen class "
                        + "remains fail closed");
    }

    private static void requireActive(String operation) {
        requireRenderThread(operation);
        if (!active || owner == null || gameDirectory == null) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope is not active for " + operation);
        }
        if (owner != Minecraft.getInstance()) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope owner drifted during " + operation);
        }
    }

    private static void requirePauseOnLostFocusDisabled(String operation) {
        if (owner.options.pauseOnLostFocus) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope observed pauseOnLostFocus=true during "
                            + operation);
        }
    }

    private static void requireRenderThread(String operation) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException(
                    "MM2 unattended UI scope must " + operation
                            + " on Minecraft's render thread");
        }
    }

    private static String bounded(String value) {
        if (value == null) {
            return "<null>";
        }
        int end = Math.min(value.length(), 180);
        return value.substring(0, end) + (end < value.length() ? "..." : "");
    }
}
