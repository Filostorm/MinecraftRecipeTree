package com.recipetree.reiexport118.compat;

import java.util.Objects;
import java.util.function.Consumer;

/** Counts and warning-logs every corrective icon render through one testable path. */
public final class NativeSpriteIconCorrectionAudit {
    private int correctionCount;

    public void record(
            NativeSpriteIconContract.CorrectionEvidence evidence,
            Consumer<String> warningSink
    ) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(warningSink, "warningSink");
        warningSink.accept(evidence.warningMessage());
        correctionCount++;
    }

    public int correctionCount() {
        return correctionCount;
    }
}
