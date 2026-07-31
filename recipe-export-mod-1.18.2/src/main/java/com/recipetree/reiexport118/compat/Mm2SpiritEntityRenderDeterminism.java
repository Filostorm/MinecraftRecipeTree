package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exporter-owned render state for Spirit's JEI entity renderer as invoked by REI's JEI adapter.
 *
 * <p>Spirit 2.1.8 copies the local player's process-age tick counter into the displayed entity
 * and passes Minecraft's frame interpolation value to the entity dispatcher. Its corrupted-entity
 * fragment shader also derives procedural RGB from RenderSystem's world {@code GameTime}. All
 * three values vary between cold launches. MM2 resolves the ingredient as
 * {@code spirit:jei_jei_compat_entityingredient}, rather than through Spirit's separate native
 * REI definition. The exact JEI-renderer-pinned mixin consults this scope only while the exporter
 * owns an offscreen capture; ordinary Spirit/JEI/REI rendering retains the upstream values.</p>
 */
public final class Mm2SpiritEntityRenderDeterminism {
    public static final int CANONICAL_ENTITY_TICK_COUNT = 0;
    public static final float CANONICAL_FRAME_TIME = 0.0F;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean INTERCEPTION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CORRUPTED_SHADER_INTERCEPTION_LOGGED =
            new AtomicBoolean();
    private static final AtomicLong PROCESS_TICK_INTERCEPTIONS = new AtomicLong();
    private static final AtomicLong PROCESS_FRAME_INTERCEPTIONS = new AtomicLong();
    private static final AtomicLong PROCESS_PAIRED_CAPTURES = new AtomicLong();
    private static final AtomicLong PROCESS_CORRUPTED_SHADER_INTERCEPTIONS = new AtomicLong();
    private static final AtomicLong PROCESS_FULLY_CORRELATED_CAPTURES = new AtomicLong();
    private static final ThreadLocal<CaptureState> ACTIVE = new ThreadLocal<>();

    private Mm2SpiritEntityRenderDeterminism() {
    }

    /** Opens a scope only after the exact MM2 lifecycle preflight has armed. */
    public static CaptureScope beginNativeCapture(String label) {
        return begin(Mm2DeterminismCompatibility.isLifecycleArmed(), label);
    }

    static CaptureScope begin(boolean armed, String label) {
        if (!armed) {
            return CaptureScope.inactive();
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("MM2 Spirit native-capture label is blank");
        }
        CaptureState prior = ACTIVE.get();
        if (prior != null) {
            throw new IllegalStateException(
                    "Nested MM2 Spirit native-capture scope: active=" + prior.label
                            + ", requested=" + label);
        }
        CaptureState state = new CaptureState(label);
        ACTIVE.set(state);
        if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 Spirit JEI-adapted entity-render "
                            + "determinism scope: "
                            + "entityTickCount={}, frameTime={}, corruptedShaderGameTime={}; "
                            + "scope=exporter-owned native "
                            + "offscreen capture only; gameplay and normal REI rendering retain "
                            + "upstream animation state",
                    CANONICAL_ENTITY_TICK_COUNT,
                    CANONICAL_FRAME_TIME,
                    Mm2SpiritShaderGameTimeContract.CANONICAL_SHADER_GAME_TIME);
        }
        return new CaptureScope(state, true);
    }

    public static boolean isCaptureActive() {
        return ACTIVE.get() != null;
    }

    /** Returns the canonical tick only inside the owned capture; otherwise preserves upstream. */
    public static int entityTickCount(int upstream) {
        CaptureState state = ACTIVE.get();
        if (state == null) {
            return upstream;
        }
        requireOwner(state, "entity tick");
        state.tickInterceptions++;
        PROCESS_TICK_INTERCEPTIONS.incrementAndGet();
        return CANONICAL_ENTITY_TICK_COUNT;
    }

    /** Returns the canonical frame value only inside the owned capture; otherwise preserves it. */
    public static float frameTime(float upstream) {
        CaptureState state = ACTIVE.get();
        if (state == null) {
            return upstream;
        }
        requireOwner(state, "frame time");
        state.frameInterceptions++;
        PROCESS_FRAME_INTERCEPTIONS.incrementAndGet();
        return CANONICAL_FRAME_TIME;
    }

    /**
     * Pins only Spirit's exact procedural corrupted-entity shader. Every other shader retains the
     * upstream world clock; the byte-pinned BufferUploader redirect supplies the shader name.
     */
    public static float corruptedShaderGameTime(float upstream, String shaderName) {
        CaptureState state = ACTIVE.get();
        if (state == null) {
            return upstream;
        }
        requireOwner(state, "corrupted shader GameTime");
        if (!Mm2SpiritShaderGameTimeContract.CORRUPTED_ENTITY_SHADER.equals(shaderName)) {
            return upstream;
        }
        state.corruptedShaderInterceptions++;
        PROCESS_CORRUPTED_SHADER_INTERCEPTIONS.incrementAndGet();
        if (CORRUPTED_SHADER_INTERCEPTION_LOGGED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[reiexport] Verified exact MM2 Spirit corrupted-entity shader clock "
                            + "interception: capture={}, shader={}, upstreamGameTime={}, "
                            + "canonicalGameTime={}",
                    state.label,
                    shaderName,
                    upstream,
                    Mm2SpiritShaderGameTimeContract.CANONICAL_SHADER_GAME_TIME);
        }
        return Mm2SpiritShaderGameTimeContract.CANONICAL_SHADER_GAME_TIME;
    }

    /** Returns cumulative process-local telemetry for an export-boundary delta audit. */
    public static AuditSnapshot auditSnapshot() {
        return new AuditSnapshot(
                PROCESS_TICK_INTERCEPTIONS.get(),
                PROCESS_FRAME_INTERCEPTIONS.get(),
                PROCESS_PAIRED_CAPTURES.get(),
                PROCESS_CORRUPTED_SHADER_INTERCEPTIONS.get(),
                PROCESS_FULLY_CORRELATED_CAPTURES.get());
    }

    /** Fails publication unless this export observed the complete known MM2 Spirit sample. */
    public static void requireObservedSince(AuditSnapshot baseline, String exportLabel) {
        if (!Mm2DeterminismCompatibility.isLifecycleArmed()) {
            return;
        }
        requireObservedSince(baseline, auditSnapshot(), exportLabel);
    }

    static void requireObservedSince(
            AuditSnapshot baseline,
            AuditSnapshot current,
            String exportLabel
    ) {
        if (baseline == null) {
            throw new IllegalArgumentException("MM2 Spirit audit baseline is null");
        }
        if (current == null) {
            throw new IllegalArgumentException("MM2 Spirit current audit snapshot is null");
        }
        if (exportLabel == null || exportLabel.isBlank()) {
            throw new IllegalArgumentException("MM2 Spirit export audit label is blank");
        }
        AuditSnapshot delta = current.minus(baseline);
        if (delta.tickInterceptions < 0 || delta.frameInterceptions < 0
                || delta.pairedCaptures < 0 || delta.corruptedShaderInterceptions < 0
                || delta.fullyCorrelatedCaptures < 0) {
            throw new IllegalStateException(
                    "MM2 Spirit audit baseline is newer than current telemetry: baseline="
                            + baseline + ", current=" + current);
        }
        requireObserved(
                delta.tickInterceptions,
                delta.frameInterceptions,
                delta.pairedCaptures,
                delta.corruptedShaderInterceptions,
                delta.fullyCorrelatedCaptures,
                "before export publication for " + exportLabel);
        LOGGER.info(
                "[reiexport] Verified exact MM2 Spirit entity-render determinism before "
                        + "publication: export={}, tickInterceptions={}, frameInterceptions={}, "
                        + "pairedCaptures={}, corruptedShaderGameTimeInterceptions={}, "
                        + "fullyCorrelatedCaptures={}",
                exportLabel,
                delta.tickInterceptions,
                delta.frameInterceptions,
                delta.pairedCaptures,
                delta.corruptedShaderInterceptions,
                delta.fullyCorrelatedCaptures);
    }

    static void requireObserved(
            long ticks,
            long frames,
            long pairedCaptures,
            long shaderInterceptions,
            long fullyCorrelatedCaptures,
            String boundary
    ) {
        if (boundary == null || boundary.isBlank()) {
            throw new IllegalArgumentException(
                    "MM2 Spirit entity-render audit boundary is blank");
        }
        if (ticks <= 0L || frames <= 0L || ticks != frames || pairedCaptures <= 0L
                || shaderInterceptions <= 0L || fullyCorrelatedCaptures <= 0L
                || pairedCaptures > ticks || fullyCorrelatedCaptures > pairedCaptures
                || fullyCorrelatedCaptures > shaderInterceptions) {
            String message = "MM2 Spirit entity-render determinism audit failed " + boundary
                    + ": tickInterceptions=" + ticks
                    + ", frameInterceptions=" + frames
                    + ", pairedCaptures=" + pairedCaptures
                    + ", corruptedShaderGameTimeInterceptions=" + shaderInterceptions
                    + ", fullyCorrelatedCaptures=" + fullyCorrelatedCaptures
                    + ". No fallback or publication was attempted.";
            LOGGER.error("[reiexport] {}", message);
            throw new IllegalStateException(message);
        }
    }

    private static void requireOwner(CaptureState state, String seam) {
        if (Thread.currentThread() != state.owner) {
            throw new IllegalStateException(
                    "MM2 Spirit " + seam + " interception crossed threads: owner="
                            + state.owner.getName() + ", caller="
                            + Thread.currentThread().getName());
        }
    }

    public record AuditSnapshot(
            long tickInterceptions,
            long frameInterceptions,
            long pairedCaptures,
            long corruptedShaderInterceptions,
            long fullyCorrelatedCaptures
    ) {
        private AuditSnapshot minus(AuditSnapshot baseline) {
            return new AuditSnapshot(
                    tickInterceptions - baseline.tickInterceptions,
                    frameInterceptions - baseline.frameInterceptions,
                    pairedCaptures - baseline.pairedCaptures,
                    corruptedShaderInterceptions - baseline.corruptedShaderInterceptions,
                    fullyCorrelatedCaptures - baseline.fullyCorrelatedCaptures);
        }
    }

    private static final class CaptureState {
        private final String label;
        private final Thread owner = Thread.currentThread();
        private int tickInterceptions;
        private int frameInterceptions;
        private int corruptedShaderInterceptions;

        private CaptureState(String label) {
            this.label = label;
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

        @Override
        public void close() {
            if (closed) {
                throw new IllegalStateException(
                        "MM2 Spirit native-capture scope closed twice");
            }
            if (!active) {
                closed = true;
                return;
            }
            requireOwner(state, "scope close");
            if (ACTIVE.get() != state) {
                throw new IllegalStateException(
                        "MM2 Spirit native-capture scope ownership drift: " + state.label);
            }
            closed = true;
            ACTIVE.remove();

            if (state.tickInterceptions != state.frameInterceptions) {
                throw new IllegalStateException(
                        "MM2 Spirit entity renderer reached only part of its exact deterministic "
                                + "contract during capture " + state.label
                                + ": tickInterceptions=" + state.tickInterceptions
                                + ", frameInterceptions=" + state.frameInterceptions);
            }
            if (state.tickInterceptions > 0) {
                PROCESS_PAIRED_CAPTURES.incrementAndGet();
                if (INTERCEPTION_LOGGED.compareAndSet(false, true)) {
                    LOGGER.info(
                            "[reiexport] Verified exact MM2 Spirit JEI entity renderer "
                                    + "interception: "
                                    + "capture={}, pairedInvocations={}",
                            state.label,
                            state.tickInterceptions);
                }
            }
            if (state.tickInterceptions > 0 && state.frameInterceptions > 0
                    && state.corruptedShaderInterceptions > 0) {
                PROCESS_FULLY_CORRELATED_CAPTURES.incrementAndGet();
            }
        }
    }
}
