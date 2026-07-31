package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LowDragFboViewportContractTest {
    @Test
    void appliesOnlyToTheExactAuditedRuntimeTuple() {
        assertTrue(LowDragFboViewportContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-1.0.8", "1.18.2-1.0.10"));
        assertFalse(LowDragFboViewportContract.isApplicable(
                "1.18.2", "40.2.18", "1.18.2-1.0.8", "1.18.2-1.0.10"));
        assertFalse(LowDragFboViewportContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-1.0.9", "1.18.2-1.0.10"));
        assertFalse(LowDragFboViewportContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-1.0.8", "1.18.2-1.0.11"));
    }

    @Test
    void correctsOnlyTheKnownViewportOmissionOnTheExporterFramebuffer() {
        assertFalse(LowDragFboViewportContract.requireViewportRestore(
                5, 384, 472, 5, 5,
                new LowDragFboViewportContract.Viewport(0, 0, 384, 472),
                new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
        assertTrue(LowDragFboViewportContract.requireViewportRestore(
                5, 384, 472, 5, 5,
                new LowDragFboViewportContract.Viewport(0, 0, 1708, 960),
                new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireViewportRestore(
                        5, 384, 472, 5, 5,
                        new LowDragFboViewportContract.Viewport(4, 7, 384, 472),
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
    }

    @Test
    void mapsPhysicalTopLeftRectsDirectlyIntoTheCaptureFramebuffer() {
        assertEquals(
                new LowDragFboViewportContract.Viewport(28, 116, 328, 280),
                LowDragFboViewportContract.requireCaptureRect(
                        384, 472, 28, 76, 328, 280, "scene"));
        assertEquals(
                new LowDragFboViewportContract.Viewport(10, 62, 128, 128),
                LowDragFboViewportContract.requireCaptureRect(
                        384, 200, 10, 10, 128, 128, "scissor"));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireCaptureRect(
                        384, 200, 300, 10, 128, 128, "outside"));
    }

    @Test
    void preservesImmediateMouseCoordinatesButRejectsPartialZeroRects() {
        assertEquals(
                new LowDragFboViewportContract.Viewport(-20_000, 20_472, 0, 0),
                LowDragFboViewportContract.requireImmediateRect(
                        384, 472, -20_000, -20_000, 0, 0));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireImmediateRect(
                        384, 472, 10, 10, 0, 20));
    }

    @Test
    void sceneDiversityGateRejectsUniformClearAndStopsAtItsThreshold() {
        int[] pixels = new int[16];
        assertEquals(0, LowDragFboViewportContract.countScenePixelDiversity(
                4, 4, 0, 0, 4, 4, 2, (x, y) -> pixels[y * 4 + x]));
        pixels[10] = 0xff;
        pixels[15] = 0xff;
        assertEquals(2, LowDragFboViewportContract.countScenePixelDiversity(
                4, 4, 0, 0, 4, 4, 2, (x, y) -> pixels[y * 4 + x]));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.countScenePixelDiversity(
                    4, 4, 3, 3, 2, 2, 1, (x, y) -> 0));
    }

    @Test
    void acceptsOnlyTheRestorableSynchronousSceneCacheLifecycle() {
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.READY,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "UNUSED", false),
                "entry");
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.FORCED_SYNCHRONOUS,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "UNUSED", false),
                "draw");
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.RESTORED,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "UNUSED", false),
                "return");
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.READY,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "NEED", false),
                "setRenderedCore invalidation");
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.FORCED_SYNCHRONOUS,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "NEED", false),
                "synchronous draw");
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.RESTORED,
                new LowDragFboViewportContract.SceneCacheSnapshot(false, "NEED", false),
                "restored invalidation");

        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.FORCED_SYNCHRONOUS,
                        new LowDragFboViewportContract.SceneCacheSnapshot(true, "UNUSED", false),
                        "cache enabled"));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.RESTORED,
                        new LowDragFboViewportContract.SceneCacheSnapshot(true, "NEED", false),
                        "restore not applied"));
    }

    @Test
    void rejectsTheAsyncProgressOverlayBeforePixelDiversityCanAcceptIt() {
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.READY,
                        new LowDragFboViewportContract.SceneCacheSnapshot(
                                true, "COMPILING", true),
                        "compiling overlay"));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.READY,
                        new LowDragFboViewportContract.SceneCacheSnapshot(
                                false, "COMPILED", false),
                        "precompiled cache"));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.READY,
                        new LowDragFboViewportContract.SceneCacheSnapshot(
                                false, "UNUSED", true),
                        "stale worker"));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireSceneCachePhase(
                        LowDragFboViewportContract.SceneCachePhase.READY,
                        new LowDragFboViewportContract.SceneCacheSnapshot(
                                false, "NEED", true),
                        "invalidation with worker"));
    }

    @Test
    void rejectsFramebufferDriftAndMalformedViewportState() {
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireViewportRestore(
                        5, 384, 472, 0, 5,
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960),
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireViewportRestore(
                        5, 384, 472, 5, 0,
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960),
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.requireViewportRestore(
                        5, 384, 472, 5, 5,
                        new LowDragFboViewportContract.Viewport(0, 0, 0, 960),
                        new LowDragFboViewportContract.Viewport(0, 0, 1708, 960)));
    }

    @Test
    void pinsAllAuditedLowDragRendererClasses() {
        assertTrue(LowDragFboViewportContract.FBO_RENDERER_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.WORLD_RENDERER_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.WORLD_RENDERER_CACHE_STATE_SHA256
                .matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.IMMEDIATE_RENDERER_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.SCENE_WIDGET_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.RENDER_UTILS_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.POSITIONED_RECT_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.MODULAR_SLOT_ENTRY_WIDGET_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.RECIPE_WIDGET_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.FUEL_WIDGET_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.RECIPE_DISPLAY_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.FUEL_DISPLAY_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.RECIPE_MAP_FUEL_DISPLAY_CATEGORY_SHA256
                .matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.PATTERN_WIDGET_SHA256.matches("[0-9a-f]{64}"));
        assertTrue(LowDragFboViewportContract.MULTIBLOCK_INFO_DISPLAY_SHA256
                .matches("[0-9a-f]{64}"));
    }

    @Test
    void distinguishesNativeFuelAndNormalRecipeIngredientGroupCounts() {
        assertEquals(1, LowDragFboViewportContract.expectedModularIngredientGroups(
                LowDragFboViewportContract.FUEL_DISPLAY_CLASS));
        assertEquals(2, LowDragFboViewportContract.expectedModularIngredientGroups(
                LowDragFboViewportContract.RECIPE_DISPLAY_CLASS));
        assertThrows(IllegalStateException.class, () ->
                LowDragFboViewportContract.expectedModularIngredientGroups(
                        "com.lowdragmc.multiblocked.rei.recipepage.FutureDisplay"));
    }
}
