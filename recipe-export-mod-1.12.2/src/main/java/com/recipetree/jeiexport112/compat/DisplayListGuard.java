package com.recipetree.jeiexport112.compat;

import com.recipetree.jeiexport112.StrictBooleanProperty;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.client.SplashProgress;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.Drawable;

/**
 * Recovers the exact macOS/LWJGL disabled-splash ownership failure observed during startup.
 * The original symptom was ModelBoat's {@code glGenLists(1) == 0}, GL error zero, and no current
 * Display drawable. The same ownership defect occurs earlier, immediately before the primary
 * block/item atlas is allocated; that path must acquire the audited Display before the first GL
 * texture allocation rather than waiting for the later display-list failure.
 *
 * <p>When Forge's splash is active, the client owns Forge's {@code SharedDrawable}. Only Forge's
 * {@link SplashProgress#pause()} / {@link SplashProgress#resume()} transfer is legal, and both
 * sides of that transfer are verified. The exporter launch has a distinct lifecycle: the launcher
 * transactionally disables the splash before JVM startup. Direct Display reacquisition is allowed
 * only when the launcher property is present, the exact Forge class bytes match the audited hash,
 * and Forge's private state proves the splash never constructed either its shared drawable or its
 * thread. This restores the same sole native context; it is not a generic context fallback.</p>
 *
 * <p>OpenGL error state belongs to the current context. After either ownership transfer is fully
 * validated, the guard therefore performs a bounded, logged drain to a terminal zero immediately
 * before its sole allocation retry. The error sampled immediately after that retry must still be
 * zero, so a newly produced error is never mistaken for stale pre-recovery state.</p>
 */
public final class DisplayListGuard {
    private static final String EXPECTED_FORGE_VERSION = "14.23.5.2860";
    /*
     * OpenGL error flags are context-local. There are fewer defined flags than this bound, so a
     * recovered context that cannot reach GL_NO_ERROR within sixteen samples is not a state in
     * which the single audited allocation retry can be attributed safely.
     */
    static final int MAX_PRE_RETRY_GL_ERROR_SAMPLES = 16;
    static final String EXPECTED_SPLASH_PROGRESS_SHA256 =
            "f91894c9af9d7daaacf7b2179a190482cacbb4f41692891fb34acdd661318682";
    private static final String DISABLED_SPLASH_POLICY_PROPERTY =
            "jeiexport.forgeSplashDisabled";

    static final class SplashOwnership {
        final boolean enabled;
        final boolean paused;
        final boolean drawablePresent;
        final boolean threadPresent;
        final boolean drawableCurrent;

        SplashOwnership(boolean enabled, boolean paused, boolean drawablePresent,
                        boolean threadPresent, boolean drawableCurrent) {
            this.enabled = enabled;
            this.paused = paused;
            this.drawablePresent = drawablePresent;
            this.threadPresent = threadPresent;
            this.drawableCurrent = drawableCurrent;
        }

        String describe() {
            return "enabled=" + enabled +
                    ", pause=" + paused +
                    ", dPresent=" + drawablePresent +
                    ", threadPresent=" + threadPresent +
                    ", dCurrent=" + drawableCurrent;
        }
    }

    interface GraphicsLifecycle {
        int generateDisplayLists(int range);

        int getError();

        boolean isDisplayCurrent() throws Exception;

        boolean isDirectDisplayRecoveryAuthorized();

        SplashOwnership inspectSplashOwnership() throws Exception;

        void makeDisplayCurrent() throws Exception;

        void pauseSplash();

        void resumeSplash();
    }

    private static final GraphicsLifecycle LWJGL_LIFECYCLE = new GraphicsLifecycle() {
        @Override
        public int generateDisplayLists(int range) {
            return GlStateManager.glGenLists(range);
        }

        @Override
        public int getError() {
            return GlStateManager.glGetError();
        }

        @Override
        public boolean isDisplayCurrent() throws Exception {
            return Display.isCurrent();
        }

        @Override
        public boolean isDirectDisplayRecoveryAuthorized() {
            return StrictBooleanProperty.read(DISABLED_SPLASH_POLICY_PROPERTY, false);
        }

        @Override
        public SplashOwnership inspectSplashOwnership() throws Exception {
            String actualForgeVersion = ForgeVersion.getVersion();
            if (!EXPECTED_FORGE_VERSION.equals(actualForgeVersion)) {
                throw new IllegalStateException(
                        "[jeiexport] Display recovery was audited only for Forge " +
                                EXPECTED_FORGE_VERSION + "; runtime reported " +
                                actualForgeVersion
                );
            }
            return ForgeSplashReflection.read();
        }

        @Override
        public void makeDisplayCurrent() throws Exception {
            Display.getDrawable().makeCurrent();
        }

        @Override
        public void pauseSplash() {
            SplashProgress.pause();
        }

        @Override
        public void resumeSplash() {
            SplashProgress.resume();
        }
    };

    /** Loaded lazily only after the exact failed-allocation signature has been observed. */
    private static final class ForgeSplashReflection {
        private static final Field ENABLED_FIELD;
        private static final Field PAUSE_FIELD;
        private static final Field DRAWABLE_FIELD;
        private static final Field THREAD_FIELD;

        static {
            verifyExactClassBytes();
            ENABLED_FIELD = resolveField("enabled", Boolean.TYPE);
            PAUSE_FIELD = resolveField("pause", Boolean.TYPE);
            DRAWABLE_FIELD = resolveField("d", Drawable.class);
            THREAD_FIELD = resolveField("thread", Thread.class);
        }

        private ForgeSplashReflection() {
        }

        private static SplashOwnership read() throws Exception {
            boolean enabled = ENABLED_FIELD.getBoolean(null);
            boolean paused = PAUSE_FIELD.getBoolean(null);
            Drawable drawable = (Drawable) DRAWABLE_FIELD.get(null);
            Thread thread = (Thread) THREAD_FIELD.get(null);
            boolean drawableCurrent = drawable != null && drawable.isCurrent();
            return new SplashOwnership(
                    enabled,
                    paused,
                    drawable != null,
                    thread != null,
                    drawableCurrent
            );
        }

        private static Field resolveField(String name, Class<?> expectedType) {
            try {
                Field field = SplashProgress.class.getDeclaredField(name);
                int modifiers = field.getModifiers();
                if (field.getType() != expectedType ||
                        !Modifier.isPrivate(modifiers) ||
                        !Modifier.isStatic(modifiers)) {
                    throw new IllegalStateException(
                            "[jeiexport] Exact Forge SplashProgress field " + name +
                                    " drifted; expected private static " +
                                    expectedType.getName()
                    );
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException exception) {
                throw new IllegalStateException(
                        "[jeiexport] Exact Forge SplashProgress field " + name +
                                " is missing; refusing unaudited Display recovery",
                        exception
                );
            } catch (SecurityException exception) {
                throw new IllegalStateException(
                        "[jeiexport] Exact Forge SplashProgress field " + name +
                                " could not be inspected; refusing unaudited Display recovery",
                        exception
                );
            }
        }

        private static void verifyExactClassBytes() {
            InputStream input = SplashProgress.class.getResourceAsStream(
                    "/net/minecraftforge/fml/client/SplashProgress.class"
            );
            if (input == null) {
                throw new IllegalStateException(
                        "[jeiexport] Could not read the installed SplashProgress.class; " +
                                "refusing unaudited Display recovery"
                );
            }

            try (InputStream closeable = input) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = closeable.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
                validateSplashProgressClassSha256(toHex(digest.digest()));
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "[jeiexport] Failed reading the installed SplashProgress.class; " +
                                "refusing unaudited Display recovery",
                        exception
                );
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(
                        "[jeiexport] SHA-256 is unavailable while auditing SplashProgress.class",
                        exception
                );
            }
        }
    }

    private DisplayListGuard() {
    }

    public static int generateDisplayLists(int range) {
        return generateDisplayLists(range, LWJGL_LIFECYCLE);
    }

    /**
     * Makes the sole Display context current after Forge's loading handoff and before Minecraft
     * creates its TextureManager, fonts, dynamic textures, or primary atlas. A missing current
     * context at this boundary leaves valid CPU pixels paired with incomplete GPU textures, which
     * fixed-function sampling represents as opaque black. This guard never reloads, substitutes,
     * or fabricates texture pixels.
     */
    public static void ensureDisplayCurrentForRendererBootstrap() {
        ensureDisplayCurrentForRendererBootstrap(LWJGL_LIFECYCLE);
    }

    static void ensureDisplayCurrentForRendererBootstrap(GraphicsLifecycle lifecycle) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("graphics lifecycle must not be null");
        }

        final boolean contextWasCurrent;
        try {
            contextWasCurrent = lifecycle.isDisplayCurrent();
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            throw new IllegalStateException(
                    "[jeiexport] Could not inspect the Display drawable immediately before " +
                            "renderer bootstrap; no GL allocation or upload was attempted.",
                    throwable
            );
        }
        if (contextWasCurrent) {
            return;
        }

        final boolean directDisplayRecoveryAuthorized;
        final SplashOwnership ownership;
        try {
            directDisplayRecoveryAuthorized =
                    lifecycle.isDirectDisplayRecoveryAuthorized();
            ownership = lifecycle.inspectSplashOwnership();
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            throw new IllegalStateException(
                    "[jeiexport] Could not verify the exact Forge splash ownership state " +
                            "before renderer bootstrap; no context operation or GL allocation " +
                            "or upload was attempted.",
                    throwable
            );
        }

        if (!directDisplayRecoveryAuthorized) {
            throw new IllegalStateException(
                    "[jeiexport] Renderer bootstrap found Display.isCurrent()=false without " +
                            "the launcher-authorized disabled-splash policy; observed " +
                            "SplashProgress{" + ownership.describe() + "}. A Forge-managed " +
                            "pause/resume cannot bracket the multi-call renderer bootstrap after " +
                            "this guard returns, so no context transfer or GL upload was attempted."
            );
        }
        requireDisabledSplashOwnership(ownership, "before renderer-bootstrap Display reacquisition");

        System.err.println(
                "[jeiexport] Renderer bootstrap reached the exact disabled-splash " +
                        "ownership defect with Display.isCurrent()=false and SplashProgress{" +
                        ownership.describe() + "}. Reacquiring the audited sole Display before " +
                        "Minecraft creates renderer textures or uploads any pixels."
        );

        try {
            lifecycle.makeDisplayCurrent();
            if (!lifecycle.isDisplayCurrent()) {
                throw new IllegalStateException(
                        "[jeiexport] Reacquiring the sole Display drawable did not make it " +
                                "current before renderer bootstrap"
                );
            }
            requireDisabledSplashOwnership(
                    lifecycle.inspectSplashOwnership(),
                    "after renderer-bootstrap Display reacquisition"
            );
            drainPreOperationGlErrors(
                    lifecycle,
                    "pre-bootstrap same-Display reacquisition",
                    "Proceeding with Minecraft's original renderer allocation/upload path."
            );
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new IllegalStateException(
                    "[jeiexport] Failed to reacquire the audited sole Display drawable before " +
                            "renderer bootstrap; no fallback context or pixel data was used.",
                    throwable
            );
        }

        System.err.println(
                "[jeiexport] Reacquired the same sole Display drawable before renderer " +
                        "bootstrap; Minecraft's original font, dynamic-texture, and atlas " +
                        "allocation/upload paths will now run with the context current."
        );
    }

    static int generateDisplayLists(int range, GraphicsLifecycle lifecycle) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("graphics lifecycle must not be null");
        }

        int displayList = lifecycle.generateDisplayLists(range);
        if (displayList != 0) {
            return displayList;
        }

        int initialError = lifecycle.getError();
        boolean contextWasCurrent;
        try {
            contextWasCurrent = lifecycle.isDisplayCurrent();
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            throw new IllegalStateException(
                    "[jeiexport] Could not inspect the Display drawable after glGenLists(" +
                            range + ") returned 0; no retry or fallback was used.",
                    throwable
            );
        }

        if (range != 1 || initialError != 0 || contextWasCurrent) {
            throw new IllegalStateException(
                    "[jeiexport] Refusing display-list recovery for an unobserved failure " +
                            "signature: range=" + range + ", initial GL error=" + initialError +
                            ", Display.isCurrent()=" + contextWasCurrent +
                            ". Expected exactly range=1, GL error 0, Display not current; " +
                            "no context operation or retry was attempted."
            );
        }

        boolean directDisplayRecoveryAuthorized;
        SplashOwnership ownership;
        try {
            directDisplayRecoveryAuthorized =
                    lifecycle.isDirectDisplayRecoveryAuthorized();
            ownership = lifecycle.inspectSplashOwnership();
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            throw new IllegalStateException(
                    "[jeiexport] Could not verify the exact Forge splash ownership state " +
                            "after glGenLists(1) returned 0; no context operation or retry " +
                            "was attempted.",
                    throwable
            );
        }

        if (directDisplayRecoveryAuthorized) {
            requireDisabledSplashOwnership(ownership, "before Display reacquisition");
        } else {
            requireActiveSplashOwnership(ownership, "before SplashProgress.pause()", true);
        }

        System.err.println(
                "[jeiexport] Observed exact display-list failure signature: range=1, " +
                        "GL error 0, Display.isCurrent()=false, launcherDisabledSplash=" +
                        directDisplayRecoveryAuthorized + ", SplashProgress{" +
                        ownership.describe() + "}. Attempting exactly one audited ownership " +
                        "recovery and allocation retry."
        );

        if (directDisplayRecoveryAuthorized) {
            return recoverDisabledSplashDisplay(lifecycle);
        }
        return recoverActiveSplashDisplay(lifecycle);
    }

    private static int recoverDisabledSplashDisplay(GraphicsLifecycle lifecycle) {
        int retryDisplayList;
        int retryError;
        try {
            lifecycle.makeDisplayCurrent();
            if (!lifecycle.isDisplayCurrent()) {
                throw new IllegalStateException(
                        "[jeiexport] Reacquiring the sole Display drawable did not make it " +
                                "current under the exact disabled-splash lifecycle"
                );
            }
            requireDisabledSplashOwnership(
                    lifecycle.inspectSplashOwnership(),
                    "after Display reacquisition"
            );
            drainPreRetryGlErrors(lifecycle, "same-Display reacquisition");
            retryDisplayList = lifecycle.generateDisplayLists(1);
            retryError = lifecycle.getError();
        } catch (Throwable throwable) {
            rethrowIfFatal(throwable);
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new IllegalStateException(
                    "[jeiexport] Failed to reacquire the audited sole Display drawable; " +
                            "no fallback context or display-list ID was used.",
                    throwable
            );
        }

        requireSuccessfulRetry(retryDisplayList, retryError, "same-Display reacquisition");
        System.err.println(
                "[jeiexport] Reacquired the same sole Display drawable under the exact " +
                        "disabled-splash state and recovered glGenLists(1) with display-list " +
                        "ID " + retryDisplayList + "; the Display context remains current."
        );
        return retryDisplayList;
    }

    private static int recoverActiveSplashDisplay(GraphicsLifecycle lifecycle) {
        Throwable failure = null;
        int retryDisplayList = 0;
        int retryError = 0;
        boolean transferAttempted = false;
        try {
            transferAttempted = true;
            lifecycle.pauseSplash();
            if (!lifecycle.isDisplayCurrent()) {
                throw new IllegalStateException(
                        "[jeiexport] Forge SplashProgress.pause() did not make the Display " +
                                "drawable current after the exact glGenLists(1) failure"
                );
            }
            requirePausedSplashOwnership(lifecycle.inspectSplashOwnership());
            drainPreRetryGlErrors(lifecycle, "Forge-managed context transfer");
            retryDisplayList = lifecycle.generateDisplayLists(1);
            retryError = lifecycle.getError();
            requireSuccessfulRetry(
                    retryDisplayList,
                    retryError,
                    "Forge-managed context transfer"
            );
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            if (transferAttempted) {
                try {
                    lifecycle.resumeSplash();
                    requireRestoredActiveSplashOwnership(lifecycle);
                } catch (Throwable restorationFailure) {
                    failure = mergeFailures(failure, restorationFailure);
                }
            }
        }

        if (failure != null) {
            rethrowIfFatal(failure);
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new IllegalStateException(
                    "[jeiexport] Forge splash context transfer failed while recovering " +
                            "glGenLists(1)",
                    failure
            );
        }

        System.err.println(
                "[jeiexport] Forge SplashProgress pause/resume recovered glGenLists(1) " +
                        "with display-list ID " + retryDisplayList +
                        " and verified restoration of the splash-owned SharedDrawable."
        );
        return retryDisplayList;
    }

    private static void drainPreRetryGlErrors(GraphicsLifecycle lifecycle,
                                              String recovery) {
        drainPreOperationGlErrors(
                lifecycle,
                recovery,
                "Proceeding with the sole glGenLists(1) retry."
        );
    }

    private static void drainPreOperationGlErrors(GraphicsLifecycle lifecycle,
                                                  String recovery,
                                                  String nextOperation) {
        int drainedCount = 0;
        StringBuilder drainedErrors = new StringBuilder();
        for (int sample = 0; sample < MAX_PRE_RETRY_GL_ERROR_SAMPLES; sample++) {
            int error = lifecycle.getError();
            if (error == 0) {
                System.err.println(
                        "[jeiexport] Completed bounded pre-operation GL error drain after " +
                                "validating " + recovery + " ownership: drained=" +
                                drainedCount + ", errors=[" + drainedErrors +
                                "], terminal GL error=0. " + nextOperation
                );
                return;
            }
            if (drainedErrors.length() > 0) {
                drainedErrors.append(", ");
            }
            drainedErrors.append(describeError(error));
            drainedCount++;
        }

        throw new IllegalStateException(
                "[jeiexport] Pre-operation GL error drain after validating " + recovery +
                        " ownership did not reach GL error 0 within the bounded " +
                        MAX_PRE_RETRY_GL_ERROR_SAMPLES + " glGetError samples; drained=" +
                        drainedCount + ", errors=[" + drainedErrors +
                        "]. The guarded next operation was not attempted; intended " +
                        "continuation was: " + nextOperation
        );
    }

    private static void requireDisabledSplashOwnership(SplashOwnership ownership,
                                                        String phase) {
        if (ownership.enabled || ownership.paused || ownership.drawablePresent ||
                ownership.threadPresent || ownership.drawableCurrent) {
            throw new IllegalStateException(
                    "[jeiexport] Launcher-authorized direct Display recovery requires exact " +
                            "uninitialized disabled SplashProgress state at " + phase +
                            " (enabled=false, pause=false, d=null, thread=null); observed {" +
                            ownership.describe() + "}. No generic property+enabled fallback " +
                            "is permitted."
            );
        }
    }

    private static void requireActiveSplashOwnership(SplashOwnership ownership,
                                                      String phase,
                                                      boolean drawableMustBeCurrent) {
        if (!ownership.enabled || ownership.paused || !ownership.drawablePresent ||
                !ownership.threadPresent ||
                ownership.drawableCurrent != drawableMustBeCurrent) {
            throw new IllegalStateException(
                    "[jeiexport] Forge active-splash ownership invariant failed at " + phase +
                            "; observed {" + ownership.describe() + "}."
            );
        }
    }

    private static void requirePausedSplashOwnership(SplashOwnership ownership) {
        if (!ownership.enabled || !ownership.paused || !ownership.drawablePresent ||
                !ownership.threadPresent || ownership.drawableCurrent) {
            throw new IllegalStateException(
                    "[jeiexport] Forge SplashProgress.pause() ownership invariant failed; " +
                            "expected Display current and SharedDrawable released, observed {" +
                            ownership.describe() + "}."
            );
        }
    }

    private static void requireRestoredActiveSplashOwnership(GraphicsLifecycle lifecycle)
            throws Exception {
        boolean displayCurrent = lifecycle.isDisplayCurrent();
        SplashOwnership restored = lifecycle.inspectSplashOwnership();
        if (displayCurrent || !restored.enabled || restored.paused ||
                !restored.drawablePresent || !restored.threadPresent ||
                !restored.drawableCurrent) {
            throw new IllegalStateException(
                    "[jeiexport] Forge SplashProgress.resume() did not restore exact shared " +
                            "ownership: Display.isCurrent()=" + displayCurrent +
                            ", SplashProgress{" + restored.describe() + "}."
            );
        }
    }

    private static void requireSuccessfulRetry(int displayList, int retryError,
                                               String recovery) {
        if (displayList == 0) {
            throw retryFailure(retryError, recovery);
        }
        if (retryError != 0) {
            throw new IllegalStateException(
                    "[jeiexport] glGenLists(1) returned display-list ID " + displayList +
                            " after the single audited " + recovery +
                            ", but the immediately sampled retry GL error was " +
                            retryError + ". Refusing the ambiguous allocation result."
            );
        }
    }

    private static IllegalStateException retryFailure(int retryError, String recovery) {
        return new IllegalStateException(
                "[jeiexport] glGenLists(1) still returned 0 after the single audited " +
                        recovery + "; initial GL error 0, retry " +
                        describeError(retryError) +
                        ". No invalid display-list ID was fabricated."
        );
    }

    static void validateSplashProgressClassSha256(String actualSha256) {
        if (!EXPECTED_SPLASH_PROGRESS_SHA256.equals(actualSha256)) {
            throw new IllegalStateException(
                    "[jeiexport] Installed SplashProgress.class SHA-256 drifted: expected " +
                            EXPECTED_SPLASH_PROGRESS_SHA256 + ", got " + actualSha256 +
                            ". Refusing unaudited Display recovery."
            );
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            output.append(String.format("%02x", value & 0xff));
        }
        return output.toString();
    }

    private static Throwable mergeFailures(Throwable primary, Throwable restorationFailure) {
        if (primary == null) {
            return restorationFailure;
        }
        if (!isFatal(primary) && isFatal(restorationFailure)) {
            if (restorationFailure != primary) {
                restorationFailure.addSuppressed(primary);
            }
            return restorationFailure;
        }
        if (restorationFailure != primary) {
            primary.addSuppressed(restorationFailure);
        }
        return primary;
    }

    private static void rethrowIfFatal(Throwable throwable) {
        if (throwable instanceof ThreadDeath) {
            throw (ThreadDeath) throwable;
        }
        if (throwable instanceof VirtualMachineError) {
            throw (VirtualMachineError) throwable;
        }
        if (throwable instanceof LinkageError) {
            throw (LinkageError) throwable;
        }
    }

    private static boolean isFatal(Throwable throwable) {
        return throwable instanceof ThreadDeath ||
                throwable instanceof VirtualMachineError ||
                throwable instanceof LinkageError;
    }

    private static String describeError(int error) {
        if (error == 0) {
            return "GL error 0 (no error code reported)";
        }
        return "GL error " + error + " (" + glErrorName(error) + ")";
    }

    private static String glErrorName(int error) {
        switch (error) {
            case 0x0500:
                return "GL_INVALID_ENUM";
            case 0x0501:
                return "GL_INVALID_VALUE";
            case 0x0502:
                return "GL_INVALID_OPERATION";
            case 0x0503:
                return "GL_STACK_OVERFLOW";
            case 0x0504:
                return "GL_STACK_UNDERFLOW";
            case 0x0505:
                return "GL_OUT_OF_MEMORY";
            case 0x0506:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";
            case 0x0507:
                return "GL_CONTEXT_LOST";
            default:
                return "UNKNOWN_GL_ERROR";
        }
    }
}
