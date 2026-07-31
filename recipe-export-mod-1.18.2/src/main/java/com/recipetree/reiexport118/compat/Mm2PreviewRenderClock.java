package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exporter-owned clock for deterministic native MM2 recipe previews.
 *
 * <p>The scope is thread-local and exists only while {@code RecipePhase} is invoking the native
 * REI widget renderers. Targeted mixins retain their upstream clocks at every other time, so this
 * does not pause animations in gameplay, menus, REI itself, or catalog-item rendering. Animated
 * catalog fluids use the separate post-render {@code NativeSpriteIconCorrector}, which selects a
 * byte-validated first native frame without touching the global texture-atlas clock.</p>
 */
public final class Mm2PreviewRenderClock {
    public enum Source {
        CREATE_RENDER_TIME,
        JEI_COMPAT_TICK_TIMER,
        LOW_DRAG_PROGRESS
    }

    /** 500 ms is a visible, non-boundary phase for both 1 s and 2 s upstream cycles. */
    public static final long CANONICAL_WALL_MILLIS = 500L;
    public static final float CANONICAL_CREATE_RENDER_TICKS = 10.0F;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ACTIVATION_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean CREATE_MIXING_VERIFIED = new AtomicBoolean();
    private static final AtomicBoolean MEKANISM_INJECTION_VERIFIED = new AtomicBoolean();
    private static final AtomicBoolean MULTIBLOCKED_CHEMICAL_VERIFIED = new AtomicBoolean();
    private static final AtomicBoolean MULTIBLOCKED_MECHANICAL_VERIFIED = new AtomicBoolean();
    private static final ThreadLocal<CaptureState> ACTIVE = new ThreadLocal<>();

    private Mm2PreviewRenderClock() {
    }

    /** Opens a deterministic clock only after the exact MM2 preflight has armed. */
    public static CaptureScope beginRecipePreview(String categoryId, int sourceIndex) {
        return begin(Mm2DeterminismCompatibility.isLifecycleArmed(), categoryId, sourceIndex);
    }

    static CaptureScope begin(boolean armed, String categoryId, int sourceIndex) {
        if (!armed) {
            return CaptureScope.inactive();
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("MM2 deterministic preview category is blank");
        }
        CaptureState prior = ACTIVE.get();
        if (prior != null) {
            throw new IllegalStateException(
                    "Nested MM2 deterministic preview clock scope: active=" + prior.label()
                            + ", requested=" + categoryId + "#" + sourceIndex);
        }
        CaptureState state = new CaptureState(categoryId, sourceIndex);
        ACTIVE.set(state);
        if (ACTIVATION_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 deterministic native-preview clock: "
                            + "wallMillis={}, createRenderTicks={}; scope=RecipePhase native draw "
                            + "only; gameplay and non-export rendering retain upstream clocks",
                    CANONICAL_WALL_MILLIS,
                    CANONICAL_CREATE_RENDER_TICKS);
        }
        return new CaptureScope(state, true);
    }

    public static boolean isCaptureActive() {
        return ACTIVE.get() != null;
    }

    public static float createRenderTime() {
        record(Source.CREATE_RENDER_TIME);
        return CANONICAL_CREATE_RENDER_TICKS;
    }

    public static long wallMillis(Source source) {
        if (source == Source.CREATE_RENDER_TIME) {
            throw new IllegalArgumentException("Create render time is a tick clock, not wall time");
        }
        record(source);
        return CANONICAL_WALL_MILLIS;
    }

    private static void record(Source source) {
        CaptureState state = ACTIVE.get();
        if (state == null) {
            throw new IllegalStateException(
                    "MM2 deterministic preview clock read outside an active recipe capture: "
                            + source);
        }
        switch (source) {
            case CREATE_RENDER_TIME -> state.createRenderTimeHits++;
            case JEI_COMPAT_TICK_TIMER -> state.jeiCompatTickTimerHits++;
            case LOW_DRAG_PROGRESS -> state.lowDragProgressHits++;
        }
    }

    private static Source expectedSource(String categoryId) {
        return switch (categoryId) {
            case "create:mixing" -> Source.CREATE_RENDER_TIME;
            case "mekanism:chemical_injection_chamber" -> Source.JEI_COMPAT_TICK_TIMER;
            case "multiblocked:chemical_reactor", "multiblocked:mechanical_crafting" ->
                    Source.LOW_DRAG_PROGRESS;
            default -> null;
        };
    }

    private static AtomicBoolean verificationLog(String categoryId) {
        return switch (categoryId) {
            case "create:mixing" -> CREATE_MIXING_VERIFIED;
            case "mekanism:chemical_injection_chamber" -> MEKANISM_INJECTION_VERIFIED;
            case "multiblocked:chemical_reactor" -> MULTIBLOCKED_CHEMICAL_VERIFIED;
            case "multiblocked:mechanical_crafting" -> MULTIBLOCKED_MECHANICAL_VERIFIED;
            default -> null;
        };
    }

    private static final class CaptureState {
        private final String categoryId;
        private final int sourceIndex;
        private final Thread owner = Thread.currentThread();
        private int createRenderTimeHits;
        private int jeiCompatTickTimerHits;
        private int lowDragProgressHits;

        private CaptureState(String categoryId, int sourceIndex) {
            this.categoryId = categoryId;
            this.sourceIndex = sourceIndex;
        }

        private String label() {
            return categoryId + "#" + sourceIndex;
        }

        private int hits(Source source) {
            return switch (source) {
                case CREATE_RENDER_TIME -> createRenderTimeHits;
                case JEI_COMPAT_TICK_TIMER -> jeiCompatTickTimerHits;
                case LOW_DRAG_PROGRESS -> lowDragProgressHits;
            };
        }

        private String hitsDescription() {
            return "{CREATE_RENDER_TIME=" + createRenderTimeHits
                    + ", JEI_COMPAT_TICK_TIMER=" + jeiCompatTickTimerHits
                    + ", LOW_DRAG_PROGRESS=" + lowDragProgressHits + "}";
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
                throw new IllegalStateException("MM2 deterministic preview clock scope closed twice");
            }
            if (!active) {
                closed = true;
                return;
            }
            if (Thread.currentThread() != state.owner) {
                throw new IllegalStateException(
                        "MM2 deterministic preview clock scope crossed threads: owner="
                                + state.owner.getName() + ", closer="
                                + Thread.currentThread().getName());
            }
            if (ACTIVE.get() != state) {
                throw new IllegalStateException(
                        "MM2 deterministic preview clock scope ownership drift: " + state.label());
            }
            closed = true;
            ACTIVE.remove();

            Source expected = expectedSource(state.categoryId);
            int interceptions = expected == null ? 0 : state.hits(expected);
            if (expected != null && interceptions == 0) {
                throw new IllegalStateException(
                        "MM2 animated native preview did not reach its byte-pinned deterministic "
                                + "clock seam: category=" + state.categoryId
                                + ", sourceIndex=" + state.sourceIndex
                                + ", expectedSource=" + expected
                                + ", observed=" + state.hitsDescription());
            }
            AtomicBoolean verificationLog = verificationLog(state.categoryId);
            if (expected != null && verificationLog != null
                    && verificationLog.compareAndSet(false, true)) {
                LOGGER.info(
                        "[reiexport] Verified deterministic native-preview clock: category={}, "
                                + "sourceIndex={}, expectedSource={}, interceptions={}, allSources={}",
                        state.categoryId,
                        state.sourceIndex,
                        expected,
                        interceptions,
                        state.hitsDescription());
            }
        }
    }
}
