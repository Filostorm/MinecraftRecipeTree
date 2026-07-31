package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2PreviewRenderClockTest {
    @Test
    void inactiveExportRetainsAnExplicitNoopScope() {
        Mm2PreviewRenderClock.CaptureScope scope =
                Mm2PreviewRenderClock.begin(false, "create:mixing", 0);
        assertFalse(Mm2PreviewRenderClock.isCaptureActive());
        scope.close();
        assertThrows(IllegalStateException.class, scope::close);
    }

    @Test
    void canonicalPhaseIsVisibleAndNonBoundaryForAllThreeNativeFormulas() {
        assertEquals(500L, Mm2PreviewRenderClock.CANONICAL_WALL_MILLIS);
        assertEquals(10.0F, Mm2PreviewRenderClock.CANONICAL_CREATE_RENDER_TICKS);

        try (Mm2PreviewRenderClock.CaptureScope ignored =
                     Mm2PreviewRenderClock.begin(true, "create:mixing", 17)) {
            assertTrue(Mm2PreviewRenderClock.isCaptureActive());
            assertEquals(10.0F, Mm2PreviewRenderClock.createRenderTime());
        }

        try (Mm2PreviewRenderClock.CaptureScope ignored =
                     Mm2PreviewRenderClock.begin(
                             true, "mekanism:chemical_injection_chamber", 23)) {
            long millis = Mm2PreviewRenderClock.wallMillis(
                    Mm2PreviewRenderClock.Source.JEI_COMPAT_TICK_TIMER);
            int ticksPerCycle = 20;
            int maxValue = 20;
            float fraction = (millis % (ticksPerCycle * 50L)) / (ticksPerCycle * 50.0F);
            assertEquals(10, Math.round(fraction * maxValue));
        }

        try (Mm2PreviewRenderClock.CaptureScope ignored =
                     Mm2PreviewRenderClock.begin(
                             true, "multiblocked:chemical_reactor", 31)) {
            long millis = Mm2PreviewRenderClock.wallMillis(
                    Mm2PreviewRenderClock.Source.LOW_DRAG_PROGRESS);
            assertEquals(0.25D, Math.abs(millis % 2_000L) / 2_000.0D);
        }
    }

    @Test
    void allFourKnownAnimatedCategoriesMustReachTheirExactClockSource() {
        verify("create:mixing", Mm2PreviewRenderClock.Source.CREATE_RENDER_TIME);
        verify("mekanism:chemical_injection_chamber",
                Mm2PreviewRenderClock.Source.JEI_COMPAT_TICK_TIMER);
        verify("multiblocked:chemical_reactor",
                Mm2PreviewRenderClock.Source.LOW_DRAG_PROGRESS);
        verify("multiblocked:mechanical_crafting",
                Mm2PreviewRenderClock.Source.LOW_DRAG_PROGRESS);
    }

    @Test
    void aWrongOrMissingInterceptionFailsVisiblyAndStillClearsTheScope() {
        Mm2PreviewRenderClock.CaptureScope missing =
                Mm2PreviewRenderClock.begin(true, "create:mixing", 4);
        IllegalStateException missingFailure =
                assertThrows(IllegalStateException.class, missing::close);
        assertTrue(missingFailure.getMessage().contains("expectedSource=CREATE_RENDER_TIME"));
        assertFalse(Mm2PreviewRenderClock.isCaptureActive());

        Mm2PreviewRenderClock.CaptureScope wrong =
                Mm2PreviewRenderClock.begin(true, "create:mixing", 5);
        Mm2PreviewRenderClock.wallMillis(Mm2PreviewRenderClock.Source.LOW_DRAG_PROGRESS);
        IllegalStateException wrongFailure =
                assertThrows(IllegalStateException.class, wrong::close);
        assertTrue(wrongFailure.getMessage().contains("LOW_DRAG_PROGRESS=1"));
        assertFalse(Mm2PreviewRenderClock.isCaptureActive());
    }

    @Test
    void nestedAndCrossThreadClockUseIsRejected() throws InterruptedException {
        try (Mm2PreviewRenderClock.CaptureScope ignored =
                     Mm2PreviewRenderClock.begin(true, "minecraft:plugins/crafting", 0)) {
            assertThrows(IllegalStateException.class, () ->
                    Mm2PreviewRenderClock.begin(true, "create:mixing", 0));
            AtomicBoolean workerSawScope = new AtomicBoolean(true);
            Thread worker = new Thread(
                    () -> workerSawScope.set(Mm2PreviewRenderClock.isCaptureActive()),
                    "preview-clock-isolation-test");
            worker.start();
            worker.join();
            assertFalse(workerSawScope.get());
        }
        assertThrows(IllegalStateException.class, Mm2PreviewRenderClock::createRenderTime);
        assertThrows(IllegalArgumentException.class, () ->
                Mm2PreviewRenderClock.wallMillis(
                        Mm2PreviewRenderClock.Source.CREATE_RENDER_TIME));
    }

    private static void verify(String category, Mm2PreviewRenderClock.Source source) {
        try (Mm2PreviewRenderClock.CaptureScope ignored =
                     Mm2PreviewRenderClock.begin(true, category, 0)) {
            if (source == Mm2PreviewRenderClock.Source.CREATE_RENDER_TIME) {
                Mm2PreviewRenderClock.createRenderTime();
            } else {
                Mm2PreviewRenderClock.wallMillis(source);
            }
        }
    }
}
