package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2SpiritEntityRenderDeterminismTest {
    @Test
    void inactiveScopePreservesBothUpstreamValues() {
        try (Mm2SpiritEntityRenderDeterminism.CaptureScope ignored =
                     Mm2SpiritEntityRenderDeterminism.begin(false, "inactive")) {
            assertFalse(Mm2SpiritEntityRenderDeterminism.isCaptureActive());
            assertEquals(731,
                    Mm2SpiritEntityRenderDeterminism.entityTickCount(731));
            assertEquals(0.625F,
                    Mm2SpiritEntityRenderDeterminism.frameTime(0.625F));
            assertEquals(0.375F,
                    Mm2SpiritEntityRenderDeterminism.corruptedShaderGameTime(
                            0.375F,
                            Mm2SpiritShaderGameTimeContract.CORRUPTED_ENTITY_SHADER));
        }
    }

    @Test
    void activeScopeCanonicalizesAndRequiresThePair() {
        try (Mm2SpiritEntityRenderDeterminism.CaptureScope ignored =
                     Mm2SpiritEntityRenderDeterminism.begin(true, "spirit-zombie")) {
            assertTrue(Mm2SpiritEntityRenderDeterminism.isCaptureActive());
            assertEquals(0,
                    Mm2SpiritEntityRenderDeterminism.entityTickCount(731));
            assertEquals(0.0F,
                    Mm2SpiritEntityRenderDeterminism.frameTime(0.625F));
            assertEquals(0.625F,
                    Mm2SpiritEntityRenderDeterminism.corruptedShaderGameTime(
                            0.625F,
                            "position_color"));
            assertEquals(Mm2SpiritShaderGameTimeContract.CANONICAL_SHADER_GAME_TIME,
                    Mm2SpiritEntityRenderDeterminism.corruptedShaderGameTime(
                            0.625F,
                            Mm2SpiritShaderGameTimeContract.CORRUPTED_ENTITY_SHADER));
        }
        assertFalse(Mm2SpiritEntityRenderDeterminism.isCaptureActive());

        Mm2SpiritEntityRenderDeterminism.CaptureScope partial =
                Mm2SpiritEntityRenderDeterminism.begin(true, "partial");
        Mm2SpiritEntityRenderDeterminism.entityTickCount(9);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                partial::close);
        assertTrue(failure.getMessage().contains("frameInterceptions=0"));
        assertFalse(Mm2SpiritEntityRenderDeterminism.isCaptureActive());
    }

    @Test
    void scopesRejectNestingDoubleCloseAndCrossThreadUse() throws Exception {
        Mm2SpiritEntityRenderDeterminism.CaptureScope scope =
                Mm2SpiritEntityRenderDeterminism.begin(true, "owner");
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.begin(true, "nested"));

        AtomicBoolean workerSawScope = new AtomicBoolean(true);
        Thread worker = new Thread(
                () -> workerSawScope.set(
                        Mm2SpiritEntityRenderDeterminism.isCaptureActive()),
                "spirit-scope-isolation-test");
        worker.start();
        worker.join();
        assertFalse(workerSawScope.get());

        scope.close();
        assertThrows(IllegalStateException.class, scope::close);
    }

    @Test
    void publicationAuditFailsClosedForEveryMissingOrUnbalancedState() {
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(0, 0, 0, 0, 0, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 0, 0, 1, 1, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 2, 1, 1, 1, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 1, 0, 1, 1, "test"));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 1, 1, 1, 1, " "));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 1, 1, 0, 0, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 1, 1, 1, 0, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(-1, -1, 1, 1, 1, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(1, 1, 2, 1, 1, "test"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObserved(2, 2, 1, 1, 2, "test"));
        Mm2SpiritEntityRenderDeterminism.requireObserved(3, 3, 2, 2, 2, "test");
    }

    @Test
    void exportDeltaAuditCannotReuseEarlierProcessEvidence() {
        Mm2SpiritEntityRenderDeterminism.AuditSnapshot prior =
                new Mm2SpiritEntityRenderDeterminism.AuditSnapshot(10, 10, 4, 3, 2);
        Mm2SpiritEntityRenderDeterminism.AuditSnapshot accepted =
                new Mm2SpiritEntityRenderDeterminism.AuditSnapshot(12, 12, 5, 4, 3);
        Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                prior,
                accepted,
                "mm2-mini-a");

        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                        accepted,
                        accepted,
                        "mm2-mini-b"));
        assertThrows(IllegalStateException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                        accepted,
                        prior,
                        "mm2-mini-b"));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                        null,
                        accepted,
                        "mm2-mini-b"));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                        prior,
                        null,
                        "mm2-mini-b"));
        assertThrows(IllegalArgumentException.class, () ->
                Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                        prior,
                        accepted,
                        " "));
    }
}
