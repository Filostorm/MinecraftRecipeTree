package com.recipetree.reiexport118.compat;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

/** Exact MM2 contract for LowDragLib's window-space assumptions during offscreen rendering. */
public final class LowDragFboViewportContract {
    public static final String MINECRAFT_VERSION = "1.18.2";
    public static final String FORGE_VERSION = "40.2.17";
    public static final String LDLIB_VERSION = "1.18.2-1.0.8";
    public static final String MULTIBLOCKED_VERSION = "1.18.2-1.0.10";

    public static final String FBO_RENDERER_CLASS =
            "com.lowdragmc.lowdraglib.client.scene.FBOWorldSceneRenderer";
    public static final String FBO_RENDERER_RESOURCE =
            "com/lowdragmc/lowdraglib/client/scene/FBOWorldSceneRenderer.class";
    public static final String FBO_RENDERER_SHA256 =
            "3266e60d96261cfcdc7b770a3f536df17c2793300df3dcc3ec14333cc2ff74fa";
    public static final String WORLD_RENDERER_CLASS =
            "com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer";
    public static final String WORLD_RENDERER_RESOURCE =
            "com/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer.class";
    public static final String WORLD_RENDERER_SHA256 =
            "6b2734541c8d95c362f49f06843f3596d5d6bcbc67f2a50178bc1a1931548edf";
    public static final String WORLD_RENDERER_CACHE_STATE_CLASS =
            "com.lowdragmc.lowdraglib.client.scene.WorldSceneRenderer$CacheState";
    public static final String WORLD_RENDERER_CACHE_STATE_RESOURCE =
            "com/lowdragmc/lowdraglib/client/scene/WorldSceneRenderer$CacheState.class";
    public static final String WORLD_RENDERER_CACHE_STATE_SHA256 =
            "d32d5276863667cec06e6340812456a34b1035b129c3487a9cd520c4ec4ed902";
    public static final String IMMEDIATE_RENDERER_CLASS =
            "com.lowdragmc.lowdraglib.client.scene.ImmediateWorldSceneRenderer";
    public static final String IMMEDIATE_RENDERER_RESOURCE =
            "com/lowdragmc/lowdraglib/client/scene/ImmediateWorldSceneRenderer.class";
    public static final String IMMEDIATE_RENDERER_SHA256 =
            "a762de9dc1036664e2305e3a0690e788029c07fc5eed0ac5899d90ef8a3d3238";
    public static final String SCENE_WIDGET_RESOURCE =
            "com/lowdragmc/lowdraglib/gui/widget/SceneWidget.class";
    public static final String SCENE_WIDGET_SHA256 =
            "45db72fadbbfa69bea671b94884926d8bf4d002f140ea8c882aa8c281a2c1935";
    public static final String RENDER_UTILS_CLASS =
            "com.lowdragmc.lowdraglib.client.utils.RenderUtils";
    public static final String RENDER_UTILS_RESOURCE =
            "com/lowdragmc/lowdraglib/client/utils/RenderUtils.class";
    public static final String RENDER_UTILS_SHA256 =
            "3376a1fa7f33454b69f9f4435b506a0439795296d1e3550390aa4c8d6b4ac9df";
    public static final String POSITIONED_RECT_CLASS =
            "com.lowdragmc.lowdraglib.utils.PositionedRect";
    public static final String POSITIONED_RECT_RESOURCE =
            "com/lowdragmc/lowdraglib/utils/PositionedRect.class";
    public static final String POSITIONED_RECT_SHA256 =
            "ddc8bff7a7915836e5f800d63c3378aacc811dd4a4df34900f5b3916f39fe2e1";
    public static final String MODULAR_SLOT_ENTRY_WIDGET_RESOURCE =
            "com/lowdragmc/lowdraglib/rei/ModularSlotEntryWidget.class";
    public static final String MODULAR_SLOT_ENTRY_WIDGET_SHA256 =
            "96542925f0d22e4830b78e58e6eb1f3cbbb76b849f8258a4440783d18d2a07b7";
    public static final String RECIPE_WIDGET_RESOURCE =
            "com/lowdragmc/multiblocked/api/gui/recipe/RecipeWidget.class";
    public static final String RECIPE_WIDGET_SHA256 =
            "f99a8d495c8ec83f41f2a1660ff71531bf508e8e01599356db5ab30353ec8cf8";
    public static final String FUEL_WIDGET_RESOURCE =
            "com/lowdragmc/multiblocked/api/gui/recipe/FuelWidget.class";
    public static final String FUEL_WIDGET_SHA256 =
            "041f2dc46af149d7f249ca4c1ccfeae1cebbbcd3a5dec6f324bc1dfeb1a50a07";
    public static final String RECIPE_DISPLAY_CLASS =
            "com.lowdragmc.multiblocked.rei.recipepage.RecipeDisplay";
    public static final String RECIPE_DISPLAY_RESOURCE =
            "com/lowdragmc/multiblocked/rei/recipepage/RecipeDisplay.class";
    public static final String RECIPE_DISPLAY_SHA256 =
            "fdab87b944f22644e736392df71a4701cff2653a8912582431a012da1a072ae2";
    public static final String FUEL_DISPLAY_CLASS =
            "com.lowdragmc.multiblocked.rei.recipepage.FuelDisplay";
    public static final String FUEL_DISPLAY_RESOURCE =
            "com/lowdragmc/multiblocked/rei/recipepage/FuelDisplay.class";
    public static final String FUEL_DISPLAY_SHA256 =
            "5120db30979bea66cef7dafec14fd90ca7814a49db40b9382805f5657743de9d";
    public static final String RECIPE_MAP_FUEL_DISPLAY_CATEGORY_RESOURCE =
            "com/lowdragmc/multiblocked/rei/recipepage/RecipeMapFuelDisplayCategory.class";
    public static final String RECIPE_MAP_FUEL_DISPLAY_CATEGORY_SHA256 =
            "5026402eb9104cef0b9357cb2dc81601e0bb325cf1d8a515e17a6b2502ea145d";
    public static final String PATTERN_WIDGET_RESOURCE =
            "com/lowdragmc/multiblocked/api/gui/controller/structure/PatternWidget.class";
    public static final String PATTERN_WIDGET_SHA256 =
            "db3cfdedbe67ffe70961d1da5749c550db0a21e9845857aca99d46529299f1d6";
    public static final String MULTIBLOCK_INFO_DISPLAY_RESOURCE =
            "com/lowdragmc/multiblocked/rei/multipage/MultiblockInfoDisplay.class";
    public static final String MULTIBLOCK_INFO_DISPLAY_SHA256 =
            "51146064e2aaba3b079c26c401cc113a9b53fe1b93dfe21327ae761c59f9358a";

    public record Viewport(int x, int y, int width, int height) {
    }

    public enum SceneCachePhase {
        READY(false),
        FORCED_SYNCHRONOUS(false),
        RESTORED(false);

        private final boolean expectedEnabled;

        SceneCachePhase(boolean expectedEnabled) {
            this.expectedEnabled = expectedEnabled;
        }
    }

    public record SceneCacheSnapshot(
            boolean enabled,
            String state,
            boolean workerPresent
    ) {
        public SceneCacheSnapshot {
            Objects.requireNonNull(state, "state");
        }
    }

    private LowDragFboViewportContract() {
    }

    public static boolean isApplicable(
            String minecraftVersion,
            String forgeVersion,
            String ldlibVersion,
            String multiblockedVersion
    ) {
        return MINECRAFT_VERSION.equals(minecraftVersion)
                && FORGE_VERSION.equals(forgeVersion)
                && LDLIB_VERSION.equals(ldlibVersion)
                && MULTIBLOCKED_VERSION.equals(multiblockedVersion);
    }

    /**
     * Returns the exact number of scrollable native ingredient groups owned by each byte-pinned
     * Multiblocked REI display implementation. A fuel display has an input group only; a normal
     * recipe display has separate input and output groups.
     */
    public static int expectedModularIngredientGroups(String displayClassName) {
        if (FUEL_DISPLAY_CLASS.equals(displayClassName)) {
            return 1;
        }
        if (RECIPE_DISPLAY_CLASS.equals(displayClassName)) {
            return 2;
        }
        throw new IllegalStateException(
                "Unaudited Multiblocked modular display class: " + displayClassName);
    }

    /**
     * Pins REI's PatternWidget(definition, true) renderer to its native synchronous lifecycle.
     * The exact REI display constructor leaves the cache disabled. PatternWidget.reset then calls
     * SceneWidget.setRenderedCore, whose byte-pinned client path calls needCompileCache and changes
     * only the inert cache marker from UNUSED to NEED. WorldSceneRenderer.drawWorld branches solely
     * on useCache, so both disabled-cache states execute the same synchronous native geometry path.
     * The exporter reasserts the disabled flag around drawWorld and restores the observed known
     * state on every exit.
     * A COMPILING state is rejected explicitly because SceneWidget otherwise draws only a
     * "Compiling scene" overlay, which is visually diverse but is not multiblock geometry.
     */
    public static void requireSceneCachePhase(
            SceneCachePhase phase,
            SceneCacheSnapshot snapshot,
            String boundary
    ) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(boundary, "boundary");
        boolean knownSynchronousState = "UNUSED".equals(snapshot.state())
                || "NEED".equals(snapshot.state());
        if (snapshot.enabled() != phase.expectedEnabled
                || !knownSynchronousState
                || snapshot.workerPresent()) {
            throw new IllegalStateException(
                    "Invalid LowDrag scene-cache lifecycle at " + boundary
                            + ": phase=" + phase
                            + ", enabled=" + snapshot.enabled()
                            + " expected=" + phase.expectedEnabled
                            + ", state=" + snapshot.state()
                            + " expected=UNUSED|NEED(setRenderedCore invalidation)"
                            + ", workerPresent=" + snapshot.workerPresent()
                            + ". Async cache/progress-overlay rendering is not publishable.");
        }
    }

    /** Converts an already pose-transformed, top-left capture rectangle to OpenGL coordinates. */
    public static Viewport requireCaptureRect(
            int captureWidth,
            int captureHeight,
            int x,
            int y,
            int width,
            int height,
            String role
    ) {
        requireCaptureDimensions(captureWidth, captureHeight);
        Objects.requireNonNull(role, "role");
        if (x < 0 || y < 0 || width < 1 || height < 1
                || (long) x + width > captureWidth
                || (long) y + height > captureHeight) {
            throw new IllegalStateException("Invalid " + role + " top-left capture rectangle: "
                    + x + "," + y + "," + width + "x" + height
                    + " outside 0,0," + captureWidth + "x" + captureHeight);
        }
        return new Viewport(x, captureHeight - y - height, width, height);
    }

    /**
     * Converts ImmediateWorldSceneRenderer's coordinates without consulting the Minecraft window.
     * A zero-size rectangle is its mouse-position carrier and may intentionally be off-canvas.
     */
    public static Viewport requireImmediateRect(
            int captureWidth,
            int captureHeight,
            int x,
            int y,
            int width,
            int height
    ) {
        if (width == 0 && height == 0) {
            requireCaptureDimensions(captureWidth, captureHeight);
            return new Viewport(x, Math.subtractExact(captureHeight, y), 0, 0);
        }
        if (width == 0 || height == 0) {
            throw new IllegalStateException(
                    "Immediate renderer returned a partially zero-sized rectangle: "
                            + x + "," + y + "," + width + "x" + height);
        }
        return requireCaptureRect(
                captureWidth, captureHeight, x, y, width, height, "Immediate scene");
    }

    /**
     * Counts pixels that differ from the scene's top-left reference pixel, stopping at the gate.
     * This keeps validation O(1) for normal scenes while still scanning a uniformly clear failure
     * completely before rejecting it.
     */
    public static long countScenePixelDiversity(
            int imageWidth,
            int imageHeight,
            int x,
            int y,
            int width,
            int height,
            long stopAfter,
            IntBinaryOperator pixelReader
    ) {
        requireCaptureRect(
                imageWidth, imageHeight, x, y, width, height, "scene validation");
        Objects.requireNonNull(pixelReader, "pixelReader");
        if (stopAfter < 1) {
            throw new IllegalArgumentException("Scene diversity stopAfter must be positive");
        }
        int reference = pixelReader.applyAsInt(x, y);
        long different = 0;
        for (int pixelY = y; pixelY < y + height; pixelY++) {
            for (int pixelX = x; pixelX < x + width; pixelX++) {
                if (pixelReader.applyAsInt(pixelX, pixelY) != reference
                        && ++different >= stopAfter) {
                    return different;
                }
            }
        }
        return different;
    }

    private static void requireCaptureDimensions(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalStateException("Invalid exporter viewport contract: "
                    + width + "x" + height);
        }
    }

    /**
     * Verifies that LowDrag returned to the exporter-owned framebuffer and reports whether its
     * known window-sized viewport omission must be corrected before the FBO texture is composited.
     */
    public static boolean requireViewportRestore(
            int expectedFramebuffer,
            int expectedWidth,
            int expectedHeight,
            int savedFramebuffer,
            int actualFramebuffer,
            Viewport actualViewport,
            Viewport auditedWindowViewport
    ) {
        Objects.requireNonNull(actualViewport, "actualViewport");
        Objects.requireNonNull(auditedWindowViewport, "auditedWindowViewport");
        if (expectedFramebuffer < 0) {
            throw new IllegalStateException(
                    "Exporter framebuffer identifier must be non-negative: " + expectedFramebuffer);
        }
        requireCaptureDimensions(expectedWidth, expectedHeight);
        if (savedFramebuffer != expectedFramebuffer) {
            throw new IllegalStateException("LowDrag nested FBO saved an unexpected framebuffer: saved="
                    + savedFramebuffer + ", exporter=" + expectedFramebuffer);
        }
        if (actualFramebuffer != expectedFramebuffer) {
            throw new IllegalStateException("LowDrag nested FBO returned to an unexpected framebuffer: actual="
                    + actualFramebuffer + ", exporter=" + expectedFramebuffer);
        }
        if (actualViewport.width() < 1 || actualViewport.height() < 1) {
            throw new IllegalStateException("LowDrag nested FBO returned an invalid viewport: "
                    + actualViewport);
        }
        Viewport exporterViewport = new Viewport(0, 0, expectedWidth, expectedHeight);
        if (actualViewport.equals(exporterViewport)) {
            return false;
        }
        if (auditedWindowViewport.x() != 0 || auditedWindowViewport.y() != 0
                || auditedWindowViewport.width() < 1 || auditedWindowViewport.height() < 1) {
            throw new IllegalStateException("Invalid audited Minecraft window viewport: "
                    + auditedWindowViewport);
        }
        if (!actualViewport.equals(auditedWindowViewport)) {
            throw new IllegalStateException(
                    "LowDrag returned an unaudited viewport; expected either exporter="
                            + exporterViewport + " or exact Minecraft window="
                            + auditedWindowViewport + ", actual=" + actualViewport);
        }
        return true;
    }
}
