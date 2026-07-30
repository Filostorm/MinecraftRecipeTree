package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exporter-owned monotonic clock for deterministic vanilla item-glint texture matrices.
 *
 * <p>The clock is active only inside an exact-MM2 offscreen capture. In particular, the scope
 * must remain open through {@code MultiBufferSource.BufferSource.endBatch()}, because deferred
 * item buffers establish their {@code RenderType} state during that flush rather than during the
 * earlier REI draw call. Minecraft's upstream clock remains authoritative on every other thread
 * and outside the scope.</p>
 */
public final class Mm2OffscreenGlintClock {
    /** A visible, non-boundary point in both vanilla glint translation periods. */
    public static final long CANONICAL_GLINT_MILLIS = 500L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_KNOWN_SAMPLE_LOGGED = new AtomicBoolean();
    private static final AtomicLong SCOPES_STARTED = new AtomicLong();
    private static final AtomicLong SCOPES_CLOSED = new AtomicLong();
    private static final AtomicLong INTERCEPTIONS = new AtomicLong();
    private static final AtomicLong VERIFIED_KNOWN_SAMPLES = new AtomicLong();
    private static final AtomicLong FAILED_KNOWN_SAMPLES = new AtomicLong();
    private static final ThreadLocal<CaptureState> ACTIVE = new ThreadLocal<>();

    private Mm2OffscreenGlintClock() {
    }

    /** Opens a scope only after the exact MM2 deterministic-export preflight has armed. */
    public static CaptureScope beginOffscreenCapture(String captureLabel) {
        return begin(Mm2DeterminismCompatibility.isLifecycleArmed(), captureLabel);
    }

    static CaptureScope begin(boolean exactMm2Armed, String captureLabel) {
        if (!exactMm2Armed) {
            return CaptureScope.inactive();
        }
        if (captureLabel == null || captureLabel.isBlank()) {
            throw new IllegalArgumentException("MM2 offscreen glint-clock capture label is blank");
        }
        CaptureState prior = ACTIVE.get();
        if (prior != null) {
            throw new IllegalStateException(
                    "Nested MM2 offscreen glint-clock scope: active=" + prior.captureLabel
                            + ", requested=" + captureLabel);
        }
        CaptureState state = new CaptureState(captureLabel);
        ACTIVE.set(state);
        SCOPES_STARTED.incrementAndGet();
        if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 offscreen glint clock: millis={}; "
                            + "scope includes native draw and deferred item-buffer flush only; "
                            + "gameplay and non-export rendering retain Minecraft's upstream clock",
                    CANONICAL_GLINT_MILLIS);
        }
        return new CaptureScope(state, true);
    }

    public static boolean isCaptureActive() {
        return ACTIVE.get() != null;
    }

    /**
     * Marks the active capture as a positive control that must execute the vanilla glint seam.
     * Call this before drawing an item whose native {@code ItemStack.hasFoil()} result is true.
     */
    public static void requireKnownSampleInterception(String evidence) {
        CaptureState state = requireActiveState("declare a known glint sample");
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("MM2 known glint-sample evidence is blank");
        }
        state.requireInterception(evidence);
    }

    /** Called only by the byte-pinned RenderStateShard redirect while a capture is active. */
    public static long canonicalGlintMillis() {
        CaptureState state = requireActiveState("read the canonical glint clock");
        state.interceptions++;
        return CANONICAL_GLINT_MILLIS;
    }

    /** Returns cumulative process-local telemetry suitable for an export-boundary delta audit. */
    public static AuditSnapshot auditSnapshot() {
        return new AuditSnapshot(
                SCOPES_STARTED.get(),
                SCOPES_CLOSED.get(),
                INTERCEPTIONS.get(),
                VERIFIED_KNOWN_SAMPLES.get(),
                FAILED_KNOWN_SAMPLES.get());
    }

    /**
     * Fails publication if no explicitly declared positive control reached the pinned seam, if a
     * declared control missed it, or if an offscreen scope remained unbalanced since baseline.
     */
    public static void requireKnownSampleInterceptionSince(
            AuditSnapshot baseline,
            String exportLabel
    ) {
        if (baseline == null) {
            throw new IllegalArgumentException("MM2 glint-clock audit baseline is null");
        }
        if (exportLabel == null || exportLabel.isBlank()) {
            throw new IllegalArgumentException("MM2 glint-clock export audit label is blank");
        }
        AuditSnapshot current = auditSnapshot();
        AuditSnapshot delta = current.minus(baseline);
        if (delta.scopesStarted < 0 || delta.scopesClosed < 0 || delta.interceptions < 0
                || delta.verifiedKnownSamples < 0 || delta.failedKnownSamples < 0) {
            throw new IllegalStateException(
                    "MM2 glint-clock audit baseline is newer than current telemetry: baseline="
                            + baseline + ", current=" + current);
        }
        if (delta.scopesStarted != delta.scopesClosed) {
            throw new IllegalStateException(
                    "MM2 offscreen glint-clock scopes are unbalanced for " + exportLabel
                            + ": delta=" + delta);
        }
        if (delta.failedKnownSamples != 0) {
            throw new IllegalStateException(
                    "MM2 known glint positive-control capture failed for " + exportLabel
                            + ": delta=" + delta);
        }
        if (delta.verifiedKnownSamples < 1 || delta.interceptions < 1) {
            throw new IllegalStateException(
                    "MM2 export did not verify a known glint sample at the byte-pinned vanilla "
                            + "clock seam for " + exportLabel + ": delta=" + delta);
        }
        LOGGER.info(
                "[reiexport] Verified exact MM2 offscreen glint-clock audit: export={}, "
                        + "scopes={}, interceptions={}, knownSamples={}",
                exportLabel,
                delta.scopesStarted,
                delta.interceptions,
                delta.verifiedKnownSamples);
    }

    private static CaptureState requireActiveState(String operation) {
        CaptureState state = ACTIVE.get();
        if (state == null) {
            throw new IllegalStateException(
                    "Cannot " + operation + " outside an active MM2 offscreen capture");
        }
        if (Thread.currentThread() != state.owner) {
            throw new IllegalStateException(
                    "MM2 offscreen glint-clock state crossed threads during " + operation
                            + ": owner=" + state.owner.getName() + ", caller="
                            + Thread.currentThread().getName());
        }
        return state;
    }

    public record AuditSnapshot(
            long scopesStarted,
            long scopesClosed,
            long interceptions,
            long verifiedKnownSamples,
            long failedKnownSamples
    ) {
        private AuditSnapshot minus(AuditSnapshot baseline) {
            return new AuditSnapshot(
                    scopesStarted - baseline.scopesStarted,
                    scopesClosed - baseline.scopesClosed,
                    interceptions - baseline.interceptions,
                    verifiedKnownSamples - baseline.verifiedKnownSamples,
                    failedKnownSamples - baseline.failedKnownSamples);
        }
    }

    private static final class CaptureState {
        private final String captureLabel;
        private final Thread owner = Thread.currentThread();
        private int interceptions;
        private boolean interceptionRequired;
        private String evidence;

        private CaptureState(String captureLabel) {
            this.captureLabel = captureLabel;
        }

        private void requireInterception(String newEvidence) {
            if (!interceptionRequired) {
                interceptionRequired = true;
                evidence = newEvidence;
                return;
            }
            if (!evidence.equals(newEvidence)) {
                evidence = evidence + "; " + newEvidence;
            }
        }
    }

    public static final class CaptureScope implements AutoCloseable {
        private final CaptureState state;
        private final boolean active;
        private boolean closed;

        private CaptureScope(CaptureState state, boolean active) {
            this.state = state;
            this.active = active;
        }

        private static CaptureScope inactive() {
            return new CaptureScope(null, false);
        }

        public int interceptionCount() {
            return state == null ? 0 : state.interceptions;
        }

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException("MM2 offscreen glint-clock scope closed twice");
            }
            if (!active) {
                closed = true;
                return;
            }
            if (Thread.currentThread() != state.owner) {
                throw new IllegalStateException(
                        "MM2 offscreen glint-clock scope crossed threads: owner="
                                + state.owner.getName() + ", closer="
                                + Thread.currentThread().getName());
            }
            if (ACTIVE.get() != state) {
                throw new IllegalStateException(
                        "MM2 offscreen glint-clock scope ownership drift: "
                                + state.captureLabel);
            }

            closed = true;
            ACTIVE.remove();
            SCOPES_CLOSED.incrementAndGet();
            INTERCEPTIONS.addAndGet(state.interceptions);

            if (state.interceptionRequired && state.interceptions == 0) {
                FAILED_KNOWN_SAMPLES.incrementAndGet();
                LOGGER.error(
                        "[reiexport] Known MM2 glint sample missed the byte-pinned vanilla clock "
                                + "seam: capture={}, evidence={}",
                        state.captureLabel,
                        state.evidence);
                throw new IllegalStateException(
                        "Known MM2 glint sample did not reach RenderStateShard's deterministic "
                                + "clock seam: capture=" + state.captureLabel + ", evidence="
                                + state.evidence + ", interceptions=0");
            }
            if (state.interceptionRequired) {
                VERIFIED_KNOWN_SAMPLES.incrementAndGet();
                if (FIRST_KNOWN_SAMPLE_LOGGED.compareAndSet(false, true)) {
                    LOGGER.info(
                            "[reiexport] Verified known MM2 glint positive control: capture={}, "
                                    + "evidence={}, interceptions={}",
                            state.captureLabel,
                            state.evidence,
                            state.interceptions);
                }
            }
        }
    }
}
