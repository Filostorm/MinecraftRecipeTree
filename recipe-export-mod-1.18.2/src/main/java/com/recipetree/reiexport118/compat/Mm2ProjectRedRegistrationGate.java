package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Canonicalizes ProjectRed's cross-module fabricated-gate registration race. */
public final class Mm2ProjectRedRegistrationGate {
    private static final AtomicReference<Boolean> OBSERVED_UPSTREAM_ENABLED =
            new AtomicReference<>();
    private static final AtomicInteger INSPECTED_GATES = new AtomicInteger();
    private static final int EXPECTED_GATE_TYPES = 35;

    private Mm2ProjectRedRegistrationGate() {
    }

    /**
     * Integration must never register its duplicate fabricated-gate item. Fabrication owns the
     * real item and injects that RegistryObject into the shared enum. Parallel mod construction
     * makes the enum appear enabled to Integration only when Fabrication initializes first.
     */
    public static boolean filterRegistration(
            boolean fabricatedGate,
            boolean upstreamEnabled
    ) {
        int inspected = INSPECTED_GATES.incrementAndGet();
        if (inspected > EXPECTED_GATE_TYPES) {
            throw new IllegalStateException(
                    "ProjectRed Integration inspected more than " + EXPECTED_GATE_TYPES
                            + " gate types; registration contract changed");
        }
        if (!fabricatedGate) {
            return registrationDecision(false, upstreamEnabled);
        }
        if (inspected != EXPECTED_GATE_TYPES) {
            throw new IllegalStateException(
                    "ProjectRed Integration FABRICATED_GATE moved from the exact final enum "
                            + "position: inspected=" + inspected
                            + ", expected=" + EXPECTED_GATE_TYPES);
        }
        if (!OBSERVED_UPSTREAM_ENABLED.compareAndSet(null, upstreamEnabled)) {
            throw new IllegalStateException(
                    "ProjectRed Integration inspected FABRICATED_GATE more than once; "
                            + "registration contract changed");
        }
        ReiExportMod.LOGGER.warn(
                "[reiexport] Canonicalized ProjectRed fabricated-gate registration: "
                        + "owner=projectred_fabrication duplicateOwner=projectred_integration "
                        + "inspectedGates={} upstreamDuplicateEnabled={} "
                        + "decision=SKIP_DUPLICATE",
                inspected, upstreamEnabled);
        return registrationDecision(true, upstreamEnabled);
    }

    public static boolean requireObservedUpstreamState() {
        Boolean observed = OBSERVED_UPSTREAM_ENABLED.get();
        if (observed == null) {
            throw new IllegalStateException(
                    "ProjectRed Integration fabricated-gate registration seam was not observed");
        }
        int inspected = INSPECTED_GATES.get();
        if (inspected != EXPECTED_GATE_TYPES) {
            throw new IllegalStateException(
                    "ProjectRed Integration gate inspection cardinality drift: expected="
                            + EXPECTED_GATE_TYPES + ", actual=" + inspected);
        }
        return observed;
    }

    static boolean registrationDecision(boolean fabricatedGate, boolean upstreamEnabled) {
        return fabricatedGate ? false : upstreamEnabled;
    }
}
