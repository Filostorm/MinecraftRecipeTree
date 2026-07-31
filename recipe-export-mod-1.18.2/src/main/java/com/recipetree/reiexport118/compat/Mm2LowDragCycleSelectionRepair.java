package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/** Selects the first LowDrag cycle-stack candidate for deterministic MM2 exports. */
public final class Mm2LowDragCycleSelectionRepair {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INSTALLATION_LOGGED = new AtomicBoolean();

    private Mm2LowDragCycleSelectionRepair() {
    }

    /**
     * Replaces the one byte-pinned wall-clock read in CycleItemStackHandler. Zero preserves the
     * native division/modulo/indexing path while deterministically selecting candidate index zero.
     */
    public static long firstCandidateEpochMillis() {
        if (INSTALLATION_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 LowDragLib cycle-stack repair: "
                            + "CycleItemStackHandler.getStackInSlot selects candidate index 0; "
                            + "wall-clock cycling is disabled only for this exact export request");
        }
        return 0L;
    }
}
