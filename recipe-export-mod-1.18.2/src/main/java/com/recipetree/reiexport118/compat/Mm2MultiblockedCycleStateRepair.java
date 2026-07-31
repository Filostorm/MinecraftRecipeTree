package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Selects a stable Multiblocked block exemplar throughout the dedicated exact-request process.
 * This deliberately includes the owned REI reload, which samples displays before ExportJob exists.
 */
public final class Mm2MultiblockedCycleStateRepair {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final InterceptionCounter INTERCEPTIONS = new InterceptionCounter();

    private Mm2MultiblockedCycleStateRepair() {
    }

    /**
     * Returns the canonical first candidate by reference. The target constructor guarantees a
     * non-empty array; violating that audited invariant is an export failure, never a fallback.
     */
    public static <T> T firstCandidate(T[] candidates) {
        if (candidates == null) {
            throw invalidCandidates("candidate array is null");
        }
        if (candidates.length == 0) {
            throw invalidCandidates("candidate array is empty");
        }
        T first = candidates[0];
        if (first == null) {
            throw invalidCandidates("candidate index 0 is null");
        }
        long interceptions = INTERCEPTIONS.record();
        if (interceptions == 1L) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 Multiblocked cycle-state repair: "
                            + "CycleBlockStateRenderer.getBlockInfo selects candidate index 0 "
                            + "from {} candidates without reading wall-clock or shared RNG state; "
                            + "cycling is disabled for the dedicated exact-request process session",
                    candidates.length);
        }
        return first;
    }

    /** Fail closed immediately after the owned reload has sampled and registered REI displays. */
    public static long requireObservedAfterOwnedReiReload() {
        return requireObserved("after owned REI display registration", false);
    }

    /** Fail closed before publication and log the final process-session interception count. */
    public static long requireObservedBeforePublication() {
        return requireObserved("before export publication", true);
    }

    private static long requireObserved(String boundary, boolean finalCount) {
        final long count;
        try {
            count = INTERCEPTIONS.requireObserved(boundary);
        } catch (IllegalStateException failure) {
            LOGGER.error(
                    "[reiexport] MM2 Multiblocked cycle-state interception was not observed {}; "
                            + "no fallback or publication was attempted",
                    boundary, failure);
            throw failure;
        }
        LOGGER.info(
                "[reiexport] Verified MM2 Multiblocked cycle-state interception {}: {}count={}",
                boundary, finalCount ? "finalProcessSession" : "processSession", count);
        return count;
    }

    private static IllegalStateException invalidCandidates(String reason) {
        String message = "MM2 Multiblocked cycle-state repair rejected an invalid audited "
                + "candidate array: " + reason;
        LOGGER.error("[reiexport] {}", message);
        return new IllegalStateException(message);
    }

    /** Package-private deterministic ledger seam; production owns exactly one process instance. */
    static final class InterceptionCounter {
        private final AtomicLong count = new AtomicLong();

        long record() {
            return count.incrementAndGet();
        }

        long requireObserved(String boundary) {
            if (boundary == null || boundary.isBlank()) {
                throw new IllegalArgumentException(
                        "MM2 cycle-state interception boundary must be nonblank");
            }
            long observed = count.get();
            if (observed == 0L) {
                throw new IllegalStateException(
                        "MM2 Multiblocked cycle-state mixin recorded zero interceptions "
                                + boundary);
            }
            return observed;
        }
    }
}
