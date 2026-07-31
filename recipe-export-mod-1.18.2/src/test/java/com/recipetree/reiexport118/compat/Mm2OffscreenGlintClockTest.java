package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2OffscreenGlintClockTest {
    @Test
    void inactiveRequestRetainsAnExplicitBalancedNoopScope() {
        Mm2OffscreenGlintClock.AuditSnapshot baseline =
                Mm2OffscreenGlintClock.auditSnapshot();
        Mm2OffscreenGlintClock.CaptureScope scope =
                Mm2OffscreenGlintClock.begin(false, "catalog:minecraft:stone");

        assertFalse(Mm2OffscreenGlintClock.isCaptureActive());
        assertEquals(0, scope.interceptionCount());
        scope.close();
        assertEquals(baseline, Mm2OffscreenGlintClock.auditSnapshot());
        assertThrows(IllegalStateException.class, scope::close);
    }

    @Test
    void canonicalPhaseIsStableAndNonBoundaryForBothVanillaTranslationPeriods() {
        assertEquals(500L, Mm2OffscreenGlintClock.CANONICAL_GLINT_MILLIS);
        long scaled = Mm2OffscreenGlintClock.CANONICAL_GLINT_MILLIS
                * Mm2OffscreenGlintClockContract.GLINT_TIME_MULTIPLIER;
        assertTrue(scaled % Mm2OffscreenGlintClockContract.GLINT_X_PERIOD_MILLIS > 0);
        assertTrue(scaled % Mm2OffscreenGlintClockContract.GLINT_Y_PERIOD_MILLIS > 0);

        Mm2OffscreenGlintClock.AuditSnapshot baseline =
                Mm2OffscreenGlintClock.auditSnapshot();
        Mm2OffscreenGlintClock.CaptureScope scope =
                Mm2OffscreenGlintClock.begin(true, "catalog:minecraft:potion");
        assertTrue(Mm2OffscreenGlintClock.isCaptureActive());
        assertEquals(500L, Mm2OffscreenGlintClock.canonicalGlintMillis());
        assertEquals(500L, Mm2OffscreenGlintClock.canonicalGlintMillis());
        assertEquals(2, scope.interceptionCount());
        scope.close();

        Mm2OffscreenGlintClock.AuditSnapshot delta = deltaFrom(baseline);
        assertEquals(1, delta.scopesStarted());
        assertEquals(1, delta.scopesClosed());
        assertEquals(2, delta.interceptions());
        assertEquals(0, delta.verifiedKnownSamples());
        assertEquals(0, delta.failedKnownSamples());
    }

    @Test
    void explicitlyDeclaredPositiveControlMustReachThePinnedSeam() {
        Mm2OffscreenGlintClock.AuditSnapshot baseline =
                Mm2OffscreenGlintClock.auditSnapshot();
        Mm2OffscreenGlintClock.CaptureScope scope =
                Mm2OffscreenGlintClock.begin(true, "catalog:minecraft:lingering_potion");
        Mm2OffscreenGlintClock.requireKnownSampleInterception(
                "ItemStack.hasFoil=true id=minecraft:lingering_potion");
        Mm2OffscreenGlintClock.canonicalGlintMillis();
        scope.close();

        Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                baseline, "unit-positive-control");
        Mm2OffscreenGlintClock.AuditSnapshot delta = deltaFrom(baseline);
        assertEquals(1, delta.verifiedKnownSamples());
        assertEquals(0, delta.failedKnownSamples());
    }

    @Test
    void missingPositiveControlInterceptionFailsVisiblyAndStillBalancesScope() {
        Mm2OffscreenGlintClock.AuditSnapshot baseline =
                Mm2OffscreenGlintClock.auditSnapshot();
        Mm2OffscreenGlintClock.CaptureScope scope =
                Mm2OffscreenGlintClock.begin(true, "catalog:minecraft:potion");
        Mm2OffscreenGlintClock.requireKnownSampleInterception(
                "ItemStack.hasFoil=true id=minecraft:potion");

        IllegalStateException failure = assertThrows(IllegalStateException.class, scope::close);
        assertTrue(failure.getMessage().contains("interceptions=0"));
        assertTrue(failure.getMessage().contains("minecraft:potion"));
        assertFalse(Mm2OffscreenGlintClock.isCaptureActive());
        Mm2OffscreenGlintClock.AuditSnapshot delta = deltaFrom(baseline);
        assertEquals(1, delta.scopesStarted());
        assertEquals(1, delta.scopesClosed());
        assertEquals(0, delta.verifiedKnownSamples());
        assertEquals(1, delta.failedKnownSamples());

        IllegalStateException auditFailure = assertThrows(IllegalStateException.class, () ->
                Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                        baseline, "unit-missing-control"));
        assertTrue(auditFailure.getMessage().contains("positive-control capture failed"));
    }

    @Test
    void aggregateAuditRejectsInterceptionsThatWereNeverDeclaredAsKnownSamples() {
        Mm2OffscreenGlintClock.AuditSnapshot baseline =
                Mm2OffscreenGlintClock.auditSnapshot();
        try (Mm2OffscreenGlintClock.CaptureScope ignored =
                     Mm2OffscreenGlintClock.begin(true, "recipe:jeed:effects#0")) {
            Mm2OffscreenGlintClock.canonicalGlintMillis();
        }

        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                        baseline, "unit-undeclared-control"));
        assertTrue(failure.getMessage().contains("did not verify a known glint sample"));
    }

    @Test
    void nestedCrossThreadAndUnbalancedUseIsRejectedWithoutLeakingOwnerState()
            throws InterruptedException {
        Mm2OffscreenGlintClock.CaptureScope scope =
                Mm2OffscreenGlintClock.begin(true, "catalog:minecraft:splash_potion");
        assertThrows(IllegalStateException.class, () ->
                Mm2OffscreenGlintClock.begin(true, "nested"));

        AtomicBoolean workerSawActive = new AtomicBoolean(true);
        AtomicReference<Throwable> workerCloseFailure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            workerSawActive.set(Mm2OffscreenGlintClock.isCaptureActive());
            try {
                scope.close();
            } catch (Throwable throwable) {
                workerCloseFailure.set(throwable);
            }
        }, "glint-clock-isolation-test");
        worker.start();
        worker.join();

        assertFalse(workerSawActive.get());
        assertTrue(workerCloseFailure.get() instanceof IllegalStateException);
        assertTrue(workerCloseFailure.get().getMessage().contains("crossed threads"));
        assertTrue(Mm2OffscreenGlintClock.isCaptureActive());
        scope.close();
        assertFalse(Mm2OffscreenGlintClock.isCaptureActive());
        assertThrows(IllegalStateException.class, scope::close);
        assertThrows(IllegalStateException.class,
                Mm2OffscreenGlintClock::canonicalGlintMillis);
        assertThrows(IllegalStateException.class, () ->
                Mm2OffscreenGlintClock.requireKnownSampleInterception("late"));
    }

    @Test
    void labelsEvidenceAndAuditBoundariesAreValidatedWithoutSilentDefaults() {
        assertThrows(IllegalArgumentException.class, () ->
                Mm2OffscreenGlintClock.begin(true, " "));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                        null, "export"));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                        Mm2OffscreenGlintClock.auditSnapshot(), ""));

        try (Mm2OffscreenGlintClock.CaptureScope ignored =
                     Mm2OffscreenGlintClock.begin(true, "catalog:test")) {
            assertThrows(IllegalArgumentException.class, () ->
                    Mm2OffscreenGlintClock.requireKnownSampleInterception(" "));
        }
    }

    private static Mm2OffscreenGlintClock.AuditSnapshot deltaFrom(
            Mm2OffscreenGlintClock.AuditSnapshot baseline
    ) {
        Mm2OffscreenGlintClock.AuditSnapshot current =
                Mm2OffscreenGlintClock.auditSnapshot();
        return new Mm2OffscreenGlintClock.AuditSnapshot(
                current.scopesStarted() - baseline.scopesStarted(),
                current.scopesClosed() - baseline.scopesClosed(),
                current.interceptions() - baseline.interceptions(),
                current.verifiedKnownSamples() - baseline.verifiedKnownSamples(),
                current.failedKnownSamples() - baseline.failedKnownSamples());
    }
}
