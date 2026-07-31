package com.recipetree.reiexport118.compat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.recipetree.reiexport118.ReiExportMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Replaces LowDragLib 1.0.8's window-relative transforms only while the exporter owns an
 * offscreen target. Every touched class is byte pinned and every capture must satisfy the native
 * scissor or world-scene execution contract before its pixels can be published.
 */
public final class LowDragFboViewportCompatibility {
    public enum CaptureMode {
        MODULAR_UI,
        MULTIBLOCK_SCENE
    }

    private static final long REQUIRED_SCENE_DIVERSITY_PIXELS = 64;

    private static final class Capture {
        private final int framebuffer;
        private final int width;
        private final int height;
        private final CaptureMode mode;
        private final String label;
        private final boolean auditSample;
        private final int expectedModularIngredientGroups;
        private int corrections;
        private int scissorFullBoundsOverrides;
        private int scissorApplications;
        private boolean awaitingTopLevelScissorApplication;
        private final List<LowDragFboViewportContract.Viewport> topLevelScissorRects =
                new ArrayList<>(2);
        private boolean scissorTargetAudited;
        private boolean modularPixelsValidated;
        private long modularDiversityPixels;
        private int immediateRectOverrides;
        private int sceneRectOverrides;
        private int mouseRectOverrides;
        private LowDragFboViewportContract.Viewport sceneTopRect;
        private LowDragFboViewportContract.Viewport sceneGlViewport;
        private Object immediateRenderer;
        private int drawStarts;
        private int drawEnds;
        private int renderedBlockGroups;
        private long renderedBlockPositions;
        private boolean sceneCacheOverrideActive;
        private LowDragFboViewportContract.SceneCacheSnapshot sceneCacheOriginal;
        private int sceneCacheOverrides;
        private int sceneCacheRestores;
        private boolean scenePixelsValidated;
        private long sceneDiversityPixels;

        private Capture(
                int framebuffer,
                int width,
                int height,
                CaptureMode mode,
                String label,
                boolean auditSample,
                int expectedModularIngredientGroups
        ) {
            this.framebuffer = framebuffer;
            this.width = width;
            this.height = height;
            this.mode = mode;
            this.label = label;
            this.auditSample = auditSample;
            this.expectedModularIngredientGroups = expectedModularIngredientGroups;
        }

        private void corrected(String source) {
            if (corrections != 0) {
                throw new IllegalStateException(
                        "LowDrag exporter capture observed more than one viewport correction; "
                                + "label=" + label + ", secondSource=" + source);
            }
            corrections = 1;
        }
    }

    private static final ThreadLocal<Capture> ACTIVE_CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<IntBuffer> VIEWPORT_QUERY = ThreadLocal.withInitial(
            () -> BufferUtils.createIntBuffer(4));
    private static final ThreadLocal<ByteBuffer> COLOR_MASK_QUERY = ThreadLocal.withInitial(
            () -> BufferUtils.createByteBuffer(4));
    private static final AtomicLong CORRECTION_COUNT = new AtomicLong();
    private static volatile Constructor<?> positionedRectConstructor;
    private static volatile Field renderedBlocksMapField;
    private static volatile Field sceneUseCacheField;
    private static volatile Field sceneCacheStateField;
    private static volatile Field sceneCacheWorkerField;
    private static volatile boolean armed;

    private LowDragFboViewportCompatibility() {
    }

    public static void validateBeforeReiRegistration() {
        armed = false;
        positionedRectConstructor = null;
        renderedBlocksMapField = null;
        sceneUseCacheField = null;
        sceneCacheStateField = null;
        sceneCacheWorkerField = null;
        String minecraftVersion = modVersion("minecraft");
        String forgeVersion = modVersion("forge");
        String ldlibVersion = modVersion("ldlib");
        String multiblockedVersion = modVersion("multiblocked");
        if (ldlibVersion == null || multiblockedVersion == null) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] LowDrag MM2 offscreen compatibility not armed because required "
                            + "mods are absent: ldlib={}, multiblocked={}",
                    ldlibVersion,
                    multiblockedVersion
            );
            return;
        }
        if (!LowDragFboViewportContract.isApplicable(
                minecraftVersion,
                forgeVersion,
                ldlibVersion,
                multiblockedVersion
        )) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] LowDrag MM2 offscreen compatibility not armed; required "
                            + "minecraft={}, forge={}, ldlib={}, multiblocked={}; actual "
                            + "minecraft={}, forge={}, ldlib={}, multiblocked={}",
                    LowDragFboViewportContract.MINECRAFT_VERSION,
                    LowDragFboViewportContract.FORGE_VERSION,
                    LowDragFboViewportContract.LDLIB_VERSION,
                    LowDragFboViewportContract.MULTIBLOCKED_VERSION,
                    minecraftVersion,
                    forgeVersion,
                    ldlibVersion,
                    multiblockedVersion
            );
            return;
        }

        List<String> failures = new ArrayList<>();
        validateClassResource(
                LowDragFboViewportContract.FBO_RENDERER_RESOURCE,
                LowDragFboViewportContract.FBO_RENDERER_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.WORLD_RENDERER_RESOURCE,
                LowDragFboViewportContract.WORLD_RENDERER_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.WORLD_RENDERER_CACHE_STATE_RESOURCE,
                LowDragFboViewportContract.WORLD_RENDERER_CACHE_STATE_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.IMMEDIATE_RENDERER_RESOURCE,
                LowDragFboViewportContract.IMMEDIATE_RENDERER_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.SCENE_WIDGET_RESOURCE,
                LowDragFboViewportContract.SCENE_WIDGET_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.RENDER_UTILS_RESOURCE,
                LowDragFboViewportContract.RENDER_UTILS_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.POSITIONED_RECT_RESOURCE,
                LowDragFboViewportContract.POSITIONED_RECT_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.MODULAR_SLOT_ENTRY_WIDGET_RESOURCE,
                LowDragFboViewportContract.MODULAR_SLOT_ENTRY_WIDGET_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.RECIPE_WIDGET_RESOURCE,
                LowDragFboViewportContract.RECIPE_WIDGET_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.FUEL_WIDGET_RESOURCE,
                LowDragFboViewportContract.FUEL_WIDGET_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.RECIPE_DISPLAY_RESOURCE,
                LowDragFboViewportContract.RECIPE_DISPLAY_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.FUEL_DISPLAY_RESOURCE,
                LowDragFboViewportContract.FUEL_DISPLAY_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.RECIPE_MAP_FUEL_DISPLAY_CATEGORY_RESOURCE,
                LowDragFboViewportContract.RECIPE_MAP_FUEL_DISPLAY_CATEGORY_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.PATTERN_WIDGET_RESOURCE,
                LowDragFboViewportContract.PATTERN_WIDGET_SHA256,
                failures);
        validateClassResource(
                LowDragFboViewportContract.MULTIBLOCK_INFO_DISPLAY_RESOURCE,
                LowDragFboViewportContract.MULTIBLOCK_INFO_DISPLAY_SHA256,
                failures);
        validateMethods(failures);
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                ReiExportMod.LOGGER.error(
                        "[reiexport] LowDrag MM2 offscreen compatibility preflight failure: {}",
                        failure);
            }
            throw new IllegalStateException(
                    "LowDrag MM2 offscreen compatibility rejected " + failures.size()
                            + " exact runtime contract(s)");
        }

        armed = true;
        ReiExportMod.LOGGER.warn(
                "[reiexport] Armed exact LowDragLib {} / Multiblocked {} offscreen compatibility: "
                        + "native modular scissor bounds, ImmediateWorldSceneRenderer positioned "
                        + "rectangles, synchronous native scene rendering, rendered-block "
                        + "geometry, disabled-cache UNUSED/benign-NEED provenance, draw-time GL "
                        + "state, and scene pixel diversity are "
                        + "byte/version pinned and fail closed",
                ldlibVersion,
                multiblockedVersion
        );
    }

    public static boolean beginExporterCapture(
            int framebuffer,
            int width,
            int height,
            CaptureMode mode,
            String label,
            boolean auditSample,
            int expectedModularIngredientGroups
    ) {
        if (!armed) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Refusing requested LowDrag capture because the exact compatibility "
                            + "contract is not armed: mode={}, label={}",
                    mode,
                    label);
            throw new IllegalStateException(
                    "Requested LowDrag offscreen capture without an armed exact runtime contract");
        }
        if (ACTIVE_CAPTURE.get() != null) {
            throw new IllegalStateException("Nested LowDrag exporter capture scope is not supported");
        }
        Objects.requireNonNull(mode, "mode");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("LowDrag exporter capture label must not be blank");
        }
        if (mode == CaptureMode.MODULAR_UI) {
            if (expectedModularIngredientGroups < 1
                    || expectedModularIngredientGroups > 2) {
                throw new IllegalArgumentException(
                        "Multiblocked modular capture requires one or two exact ingredient groups: label="
                                + label + ", expected=" + expectedModularIngredientGroups);
            }
        } else if (expectedModularIngredientGroups != 0) {
            throw new IllegalArgumentException(
                    "Non-modular LowDrag capture cannot declare ingredient groups: mode="
                            + mode + ", label=" + label
                            + ", expected=" + expectedModularIngredientGroups);
        }
        LowDragFboViewportContract.requireViewportRestore(
                framebuffer,
                width,
                height,
                framebuffer,
                framebuffer,
                new LowDragFboViewportContract.Viewport(0, 0, width, height),
                windowViewport());
        ACTIVE_CAPTURE.set(new Capture(
                framebuffer, width, height, mode, label, auditSample,
                expectedModularIngredientGroups));
        return true;
    }

    public static void endExporterCapture() {
        Capture capture = ACTIVE_CAPTURE.get();
        if (capture == null) {
            throw new IllegalStateException(
                    "LowDrag exporter capture scope ended without a matching begin");
        }
        try {
            if (capture.sceneCacheOverrideActive) {
                restoreImmediateSceneCache(capture, "exceptional capture teardown");
            }
            requireBalancedScissorContract(capture);
            if (capture.mode == CaptureMode.MODULAR_UI) {
                requireModularUiCompletion(capture);
            } else if (capture.mode == CaptureMode.MULTIBLOCK_SCENE) {
                requireMultiblockSceneCompletion(capture);
            } else {
                throw new IllegalStateException("Unhandled LowDrag capture mode " + capture.mode);
            }
            if (capture.auditSample) {
                ReiExportMod.LOGGER.info(
                        "[reiexport] LowDrag offscreen contract passed: label={}, mode={}, target={}x{}, "
                                + "scissorFullBoundsOverrides={}, scissorApplications={}, "
                                + "expectedModularIngredientGroups={}, topLevelScissorRects={}, "
                                + "modularDiversityPixelsAtLeast={}, "
                                + "immediateRects={}(scene={},mouse={}), worldDraw={}/{}, "
                                + "synchronousSceneCache={}/{}, renderedBlockGroups={}, "
                                + "renderedBlockPositions={}, sceneTopRect={}, sceneGlViewport={}, "
                                + "viewportCorrections={}, sceneDiversityPixelsAtLeast={}",
                        capture.label,
                        capture.mode,
                        capture.width,
                        capture.height,
                        capture.scissorFullBoundsOverrides,
                        capture.scissorApplications,
                        capture.expectedModularIngredientGroups,
                        capture.topLevelScissorRects,
                        capture.modularDiversityPixels,
                        capture.immediateRectOverrides,
                        capture.sceneRectOverrides,
                        capture.mouseRectOverrides,
                        capture.drawStarts,
                        capture.drawEnds,
                        capture.sceneCacheOverrides,
                        capture.sceneCacheRestores,
                        capture.renderedBlockGroups,
                        capture.renderedBlockPositions,
                        capture.sceneTopRect,
                        capture.sceneGlViewport,
                        capture.corrections,
                        capture.sceneDiversityPixels
                );
            }
        } finally {
            ACTIVE_CAPTURE.remove();
        }
    }

    /** Replaces only RenderUtils' exact empty-stack Minecraft-window bounds. */
    public static int[] replaceWindowScissorBounds(int[] originalBounds) {
        Capture capture = activeCapture();
        if (capture == null) {
            return null;
        }
        if (originalBounds == null || originalBounds.length != 4) {
            throw new IllegalStateException(
                    "LowDrag RenderUtils returned malformed scissor bounds during " + capture.label);
        }
        LowDragFboViewportContract.Viewport window = windowViewport();
        boolean fullWindow = originalBounds[0] == window.x()
                && originalBounds[1] == window.y()
                && originalBounds[2] == window.width()
                && originalBounds[3] == window.height();
        if (!fullWindow) {
            LowDragFboViewportContract.requireCaptureRect(
                    capture.width,
                    capture.height,
                    originalBounds[0],
                    originalBounds[1],
                    originalBounds[2],
                    originalBounds[3],
                    "nested LowDrag scissor");
            return null;
        }
        if (capture.awaitingTopLevelScissorApplication) {
            throw new IllegalStateException(
                    "LowDrag requested new full scissor bounds before applying the prior bounds "
                            + "during " + capture.label);
        }
        capture.scissorFullBoundsOverrides++;
        capture.awaitingTopLevelScissorApplication = true;
        return new int[]{0, 0, capture.width, capture.height};
    }

    /** Applies an already physical, top-left LowDrag scissor directly to the capture target. */
    public static boolean applyExporterScissor(int x, int y, int width, int height) {
        Capture capture = activeCapture();
        if (capture == null) {
            return false;
        }
        LowDragFboViewportContract.Viewport scissor =
                LowDragFboViewportContract.requireCaptureRect(
                        capture.width,
                        capture.height,
                        x,
                        y,
                        width,
                        height,
                        "LowDrag scissor");
        if (!capture.scissorTargetAudited) {
            // The exporter itself just bound this target. Preserve the existing sampled-audit
            // policy so a large recipe category does not incur two synchronous driver queries
            // per recipe merely to reconfirm exporter-owned state.
            if (capture.auditSample) {
                requireTargetState(
                        capture,
                        new LowDragFboViewportContract.Viewport(
                                0, 0, capture.width, capture.height),
                        "first native scissor application");
            }
            capture.scissorTargetAudited = true;
        }
        GL11.glScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
        if (capture.awaitingTopLevelScissorApplication) {
            capture.awaitingTopLevelScissorApplication = false;
            boolean fullCapture = x == 0 && y == 0
                    && width == capture.width && height == capture.height;
            if (!fullCapture && !capture.topLevelScissorRects.contains(
                    new LowDragFboViewportContract.Viewport(x, y, width, height))) {
                capture.topLevelScissorRects.add(
                        new LowDragFboViewportContract.Viewport(x, y, width, height));
            }
        }
        capture.scissorApplications++;
        return true;
    }

    /** Returns the exact capture-space PositionedRect for the gated Immediate renderer. */
    public static Object overrideImmediatePositionedRect(
            String rendererClassName,
            Object renderer,
            int x,
            int y,
            int width,
            int height
    ) {
        Capture capture = activeCapture();
        if (capture == null) {
            return null;
        }
        if (!LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS.equals(rendererClassName)) {
            throw new IllegalStateException(
                    "Unaudited ImmediateWorldSceneRenderer subclass during " + capture.label
                            + ": " + rendererClassName);
        }
        if (capture.mode != CaptureMode.MULTIBLOCK_SCENE) {
            throw new IllegalStateException(
                    "Immediate world rendering unexpectedly entered modular-UI capture "
                            + capture.label);
        }
        if (capture.immediateRenderer == null) {
            capture.immediateRenderer = renderer;
        } else if (capture.immediateRenderer != renderer) {
            throw new IllegalStateException(
                    "More than one ImmediateWorldSceneRenderer entered capture " + capture.label);
        }

        LowDragFboViewportContract.Viewport rect =
                LowDragFboViewportContract.requireImmediateRect(
                        capture.width, capture.height, x, y, width, height);
        capture.immediateRectOverrides++;
        if (width > 0 && height > 0) {
            if (capture.sceneRectOverrides != 0) {
                throw new IllegalStateException(
                        "More than one Immediate scene rectangle entered capture " + capture.label);
            }
            capture.sceneRectOverrides = 1;
            capture.sceneTopRect = new LowDragFboViewportContract.Viewport(x, y, width, height);
            capture.sceneGlViewport = rect;
        } else {
            if (capture.sceneRectOverrides != 1) {
                throw new IllegalStateException(
                        "Immediate mouse rectangle preceded the scene rectangle for " + capture.label);
            }
            capture.mouseRectOverrides++;
        }

        Constructor<?> constructor = positionedRectConstructor;
        if (constructor == null) {
            throw new IllegalStateException(
                    "PositionedRect constructor unavailable after compatibility preflight");
        }
        try {
            return constructor.newInstance(
                    rect.x(), rect.y(), rect.width(), rect.height());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Constructing the byte-pinned LowDrag PositionedRect failed", exception);
        }
    }

    /** Audits actual native geometry and GL state at the exact drawWorld entry point. */
    public static void beforeImmediateWorldDraw(String rendererClassName, Object renderer) {
        Capture capture = activeCapture();
        if (capture == null) {
            return;
        }
        requireExactSceneRenderer(capture, rendererClassName, renderer);
        if (++capture.drawStarts != 1) {
            throw new IllegalStateException(
                    "Immediate world geometry drew more than once during " + capture.label);
        }
        if (capture.sceneGlViewport == null) {
            throw new IllegalStateException(
                    "Immediate world draw began without a capture-space scene viewport for "
                            + capture.label);
        }

        Field field = renderedBlocksMapField;
        if (field == null) {
            throw new IllegalStateException(
                    "renderedBlocksMap field unavailable after compatibility preflight");
        }
        Object value;
        try {
            value = field.get(renderer);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Reading LowDrag renderedBlocksMap failed", exception);
        }
        if (!(value instanceof Map<?, ?> renderedBlocks)) {
            throw new IllegalStateException(
                    "LowDrag renderedBlocksMap is not a Map during " + capture.label);
        }
        long positions = 0;
        for (Object key : renderedBlocks.keySet()) {
            if (!(key instanceof Collection<?> blocks)) {
                throw new IllegalStateException(
                        "LowDrag renderedBlocksMap contains a non-Collection key during "
                                + capture.label);
            }
            positions = Math.addExact(positions, blocks.size());
        }
        capture.renderedBlockGroups = renderedBlocks.size();
        capture.renderedBlockPositions = positions;
        if (capture.renderedBlockGroups < 1 || positions < 1) {
            throw new IllegalStateException(
                    "Multiblock scene has no native rendered-block geometry: label="
                            + capture.label + ", groups=" + capture.renderedBlockGroups
                            + ", positions=" + positions);
        }

        requireTargetState(capture, capture.sceneGlViewport, "Immediate drawWorld entry");
        if (!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                || !GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                || GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            throw new IllegalStateException(
                    "Invalid draw-time depth/scissor state for " + capture.label
                            + ": depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                            + ", depthWrite=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                            + ", scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
        }
        ByteBuffer colorMask = COLOR_MASK_QUERY.get();
        colorMask.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        if (colorMask.get(0) == 0 || colorMask.get(1) == 0
                || colorMask.get(2) == 0 || colorMask.get(3) == 0) {
            throw new IllegalStateException(
                    "Immediate drawWorld entered with a disabled color channel during "
                            + capture.label);
        }
        forceSynchronousImmediateSceneCache(capture, renderer);
    }

    /** Verifies that native world drawing did not redirect the target or scene viewport. */
    public static void afterImmediateWorldDraw(String rendererClassName, Object renderer) {
        Capture capture = activeCapture();
        if (capture == null) {
            return;
        }
        requireExactSceneRenderer(capture, rendererClassName, renderer);
        if (capture.drawStarts != 1 || ++capture.drawEnds != 1) {
            throw new IllegalStateException(
                    "Unbalanced Immediate world draw callbacks during " + capture.label);
        }
        try {
            requireTargetState(capture, capture.sceneGlViewport, "Immediate drawWorld return");
        } finally {
            restoreImmediateSceneCache(capture, "drawWorld return");
        }
    }

    /** Rejects a uniformly cleared scene viewport after native texture readback. */
    public static void validateCapturedImage(NativeImage image) {
        Capture capture = activeCapture();
        if (capture == null) {
            return;
        }
        if (image.getWidth() != capture.width || image.getHeight() != capture.height) {
            throw new IllegalStateException(
                    "LowDrag capture image dimensions drifted during " + capture.label);
        }
        if (capture.mode == CaptureMode.MODULAR_UI) {
            validateModularUiPixels(capture, image);
            return;
        }
        if (capture.sceneCacheOverrideActive
                || capture.sceneCacheOverrides != 1
                || capture.sceneCacheRestores != 1) {
            throw new IllegalStateException(
                    "Native multiblock image reached readback without one restored synchronous "
                            + "scene draw: label=" + capture.label
                            + ", cacheOverrides=" + capture.sceneCacheOverrides
                            + ", cacheRestores=" + capture.sceneCacheRestores
                            + ", overrideActive=" + capture.sceneCacheOverrideActive);
        }
        requireSceneCachePhase(
                capture,
                LowDragFboViewportContract.SceneCachePhase.RESTORED,
                "native image readback");
        LowDragFboViewportContract.Viewport scene = capture.sceneTopRect;
        if (scene == null) {
            throw new IllegalStateException(
                    "LowDrag scene readback occurred without an audited scene rectangle for "
                            + capture.label);
        }
        long sceneArea = Math.multiplyExact((long) scene.width(), scene.height());
        long required = Math.min(REQUIRED_SCENE_DIVERSITY_PIXELS, sceneArea - 1);
        if (required < 1) {
            throw new IllegalStateException(
                    "LowDrag scene viewport is too small to validate during " + capture.label);
        }
        long diversity = LowDragFboViewportContract.countScenePixelDiversity(
                capture.width,
                capture.height,
                scene.x(),
                scene.y(),
                scene.width(),
                scene.height(),
                required,
                image::getPixelRGBA);
        if (diversity < required) {
            int reference = image.getPixelRGBA(scene.x(), scene.y());
            throw new IllegalStateException(
                    "Native multiblock scene viewport remained uniformly cleared or nearly "
                            + "uniform: label=" + capture.label + ", topRect=" + scene
                            + ", differingPixels=" + diversity + ", required=" + required
                            + ", referenceNativeRgba=0x" + Integer.toHexString(reference));
        }
        capture.scenePixelsValidated = true;
        capture.sceneDiversityPixels = diversity;
    }

    /** Selects the exact synchronous branch in WorldSceneRenderer.drawWorld for this capture. */
    private static void forceSynchronousImmediateSceneCache(Capture capture, Object renderer) {
        if (capture.sceneCacheOverrideActive
                || capture.sceneCacheOverrides != 0
                || capture.sceneCacheRestores != 0) {
            throw new IllegalStateException(
                    "LowDrag synchronous scene-cache override was entered more than once during "
                            + capture.label);
        }
        LowDragFboViewportContract.SceneCacheSnapshot original = sceneCacheSnapshot(capture);
        LowDragFboViewportContract.requireSceneCachePhase(
                LowDragFboViewportContract.SceneCachePhase.READY,
                original,
                capture.label + " / drawWorld entry");
        if (capture.auditSample && "NEED".equals(original.state())) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Accepted byte-pinned LowDrag setRenderedCore cache invalidation "
                            + "for synchronous native scene draw: label={}, useCache=false, "
                            + "cacheState=NEED, worker=null",
                    capture.label);
        }
        capture.sceneCacheOriginal = original;
        Field useCache = sceneUseCacheField;
        if (useCache == null) {
            throw new IllegalStateException(
                    "WorldSceneRenderer.useCache field unavailable after compatibility preflight");
        }
        try {
            useCache.setBoolean(renderer, false);
            capture.sceneCacheOverrideActive = true;
            requireSceneCachePhase(
                    capture,
                    LowDragFboViewportContract.SceneCachePhase.FORCED_SYNCHRONOUS,
                    "drawWorld synchronous override");
            capture.sceneCacheOverrides = 1;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Disabling the byte-pinned LowDrag asynchronous scene cache failed",
                    exception);
        } catch (RuntimeException contractFailure) {
            if (capture.sceneCacheOverrideActive) {
                try {
                    restoreImmediateSceneCache(capture, "failed synchronous override validation");
                } catch (RuntimeException restoreFailure) {
                    contractFailure.addSuppressed(restoreFailure);
                }
            }
            throw contractFailure;
        }
    }

    /** Restores PatternWidget's exact cache state on normal return and exceptional teardown. */
    private static void restoreImmediateSceneCache(Capture capture, String boundary) {
        if (!capture.sceneCacheOverrideActive
                || capture.immediateRenderer == null
                || capture.sceneCacheOriginal == null) {
            throw new IllegalStateException(
                    "LowDrag scene-cache restoration had no active override during "
                            + capture.label + " at " + boundary);
        }

        RuntimeException forcedStateFailure = null;
        try {
            requireSceneCachePhase(
                    capture,
                    LowDragFboViewportContract.SceneCachePhase.FORCED_SYNCHRONOUS,
                    boundary + " before restoration");
        } catch (RuntimeException failure) {
            forcedStateFailure = failure;
        }

        RuntimeException restorationFailure = null;
        Field useCache = sceneUseCacheField;
        try {
            if (useCache == null) {
                throw new IllegalStateException(
                        "WorldSceneRenderer.useCache field unavailable during restoration");
            }
            useCache.setBoolean(
                    capture.immediateRenderer,
                    capture.sceneCacheOriginal.enabled());
            capture.sceneCacheOverrideActive = false;
            LowDragFboViewportContract.SceneCacheSnapshot restored =
                    sceneCacheSnapshot(capture);
            LowDragFboViewportContract.requireSceneCachePhase(
                    LowDragFboViewportContract.SceneCachePhase.RESTORED,
                    restored,
                    capture.label + " / " + boundary + " after restoration");
            if (!capture.sceneCacheOriginal.equals(restored)) {
                throw new IllegalStateException(
                        "LowDrag scene cache did not restore its exact original state during "
                                + capture.label + ": original=" + capture.sceneCacheOriginal
                                + ", restored=" + restored);
            }
            capture.sceneCacheRestores++;
        } catch (IllegalAccessException exception) {
            restorationFailure = new IllegalStateException(
                    "Restoring the byte-pinned LowDrag asynchronous scene-cache flag failed",
                    exception);
        } catch (RuntimeException failure) {
            restorationFailure = failure;
        }

        if (forcedStateFailure != null) {
            if (restorationFailure != null) {
                forcedStateFailure.addSuppressed(restorationFailure);
            }
            throw forcedStateFailure;
        }
        if (restorationFailure != null) {
            throw restorationFailure;
        }
    }

    private static void requireSceneCachePhase(
            Capture capture,
            LowDragFboViewportContract.SceneCachePhase phase,
            String boundary
    ) {
        LowDragFboViewportContract.requireSceneCachePhase(
                phase,
                sceneCacheSnapshot(capture),
                capture.label + " / " + boundary);
    }

    private static LowDragFboViewportContract.SceneCacheSnapshot sceneCacheSnapshot(
            Capture capture
    ) {
        Field useCache = sceneUseCacheField;
        Field cacheState = sceneCacheStateField;
        Field worker = sceneCacheWorkerField;
        if (useCache == null || cacheState == null || worker == null
                || capture.immediateRenderer == null) {
            throw new IllegalStateException(
                    "LowDrag scene-cache reflection contract is unavailable during "
                            + capture.label);
        }
        try {
            boolean enabled = useCache.getBoolean(capture.immediateRenderer);
            Object holderValue = cacheState.get(capture.immediateRenderer);
            if (!(holderValue instanceof AtomicReference<?> holder)) {
                throw new IllegalStateException(
                        "WorldSceneRenderer.cacheState is not an AtomicReference during "
                                + capture.label);
            }
            Object stateValue = holder.get();
            if (!(stateValue instanceof Enum<?> state)
                    || !LowDragFboViewportContract.WORLD_RENDERER_CACHE_STATE_CLASS.equals(
                    stateValue.getClass().getName())) {
                throw new IllegalStateException(
                        "WorldSceneRenderer.cacheState contained an unaudited value during "
                                + capture.label + ": "
                                + (stateValue == null ? "null" : stateValue.getClass().getName()));
            }
            Object workerValue = worker.get(capture.immediateRenderer);
            if (workerValue != null && !(workerValue instanceof Thread)) {
                throw new IllegalStateException(
                        "WorldSceneRenderer.thread contained an unaudited value during "
                                + capture.label + ": " + workerValue.getClass().getName());
            }
            return new LowDragFboViewportContract.SceneCacheSnapshot(
                    enabled,
                    state.name(),
                    workerValue != null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Inspecting the byte-pinned LowDrag scene-cache state failed",
                    exception);
        }
    }

    /** Called by the exact gated mixin at the return of FBOWorldSceneRenderer.unbindFBO. */
    public static void restoreExporterViewportAfterNestedFbo(int savedFramebuffer) {
        Capture capture = activeCapture();
        if (capture == null) {
            return;
        }
        int actualFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        LowDragFboViewportContract.Viewport viewport = currentViewport();
        boolean restore = LowDragFboViewportContract.requireViewportRestore(
                capture.framebuffer,
                capture.width,
                capture.height,
                savedFramebuffer,
                actualFramebuffer,
                viewport,
                windowViewport());
        if (!restore) {
            return;
        }
        restoreFullCaptureViewport(capture, actualFramebuffer, viewport, "nested-FBO unbind");
    }

    /** Called immediately after exact LowDrag WorldSceneRenderer.resetCamera returns. */
    public static void restoreExporterViewportAfterImmediateReset(String rendererClassName) {
        Capture capture = activeCapture();
        if (capture == null
                || !LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS.equals(rendererClassName)) {
            return;
        }
        if (capture.mode != CaptureMode.MULTIBLOCK_SCENE) {
            throw new IllegalStateException(
                    "Immediate resetCamera entered non-scene capture " + capture.label);
        }
        int actualFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        LowDragFboViewportContract.Viewport viewport = currentViewport();
        boolean restore = LowDragFboViewportContract.requireViewportRestore(
                capture.framebuffer,
                capture.width,
                capture.height,
                capture.framebuffer,
                actualFramebuffer,
                viewport,
                windowViewport());
        if (!restore) {
            throw new IllegalStateException(
                    "Exact LowDrag ImmediateWorldSceneRenderer.resetCamera did not expose the "
                            + "audited Minecraft-window viewport transition during " + capture.label);
        }
        restoreFullCaptureViewport(capture, actualFramebuffer, viewport, "Immediate resetCamera");
    }

    public static boolean isArmed() {
        return armed;
    }

    public static long correctionCount() {
        return CORRECTION_COUNT.get();
    }

    private static Capture activeCapture() {
        if (!armed) {
            return null;
        }
        return ACTIVE_CAPTURE.get();
    }

    private static void requireBalancedScissorContract(Capture capture) {
        if ((capture.scissorFullBoundsOverrides & 1) != 0
                || (capture.scissorApplications & 1) != 0
                || capture.scissorApplications < capture.scissorFullBoundsOverrides
                || capture.awaitingTopLevelScissorApplication) {
            throw new IllegalStateException(
                    "Unbalanced LowDrag scissor callbacks: label=" + capture.label
                            + ", fullBoundsOverrides=" + capture.scissorFullBoundsOverrides
                            + ", applications=" + capture.scissorApplications);
        }
    }

    private static void requireModularUiCompletion(Capture capture) {
        int minimumScissorCallbacks = Math.multiplyExact(
                capture.expectedModularIngredientGroups, 2);
        if (capture.scissorFullBoundsOverrides < minimumScissorCallbacks
                || capture.scissorApplications < minimumScissorCallbacks) {
            throw new IllegalStateException(
                    "Native Multiblocked recipe widget did not execute every audited scrollable "
                            + "ingredient group: label=" + capture.label
                            + ", expectedGroups="
                            + capture.expectedModularIngredientGroups
                            + ", fullBoundsOverrides=" + capture.scissorFullBoundsOverrides
                            + ", applications=" + capture.scissorApplications);
        }
        if (capture.topLevelScissorRects.size()
                != capture.expectedModularIngredientGroups
                || !capture.modularPixelsValidated) {
            throw new IllegalStateException(
                    "Native Multiblocked ingredient-group pixel validation did not complete: "
                            + "label=" + capture.label + ", expectedGroups="
                            + capture.expectedModularIngredientGroups
                            + ", topLevelScissorRects="
                            + capture.topLevelScissorRects.size() + ", validated="
                            + capture.modularPixelsValidated);
        }
        if (capture.corrections != 0 || capture.immediateRectOverrides != 0
                || capture.drawStarts != 0 || capture.drawEnds != 0
                || capture.sceneCacheOverrideActive
                || capture.sceneCacheOriginal != null
                || capture.sceneCacheOverrides != 0
                || capture.sceneCacheRestores != 0
                || capture.scenePixelsValidated) {
            throw new IllegalStateException(
                    "World-scene callbacks unexpectedly entered modular-UI capture " + capture.label);
        }
    }

    private static void requireMultiblockSceneCompletion(Capture capture) {
        if (capture.immediateRectOverrides != 2
                || capture.sceneRectOverrides != 1
                || capture.mouseRectOverrides != 1
                || capture.drawStarts != 1
                || capture.drawEnds != 1
                || capture.sceneCacheOverrideActive
                || capture.sceneCacheOriginal == null
                || capture.sceneCacheOverrides != 1
                || capture.sceneCacheRestores != 1
                || capture.renderedBlockGroups < 1
                || capture.renderedBlockPositions < 1
                || capture.corrections != 1
                || !capture.scenePixelsValidated) {
            throw new IllegalStateException(
                    "Incomplete native multiblock scene capture: label=" + capture.label
                            + ", immediateRects=" + capture.immediateRectOverrides
                            + "(scene=" + capture.sceneRectOverrides
                            + ",mouse=" + capture.mouseRectOverrides + ")"
                            + ", worldDraw=" + capture.drawStarts + "/" + capture.drawEnds
                            + ", synchronousSceneCache=" + capture.sceneCacheOverrides
                            + "/" + capture.sceneCacheRestores
                            + " active=" + capture.sceneCacheOverrideActive
                            + ", renderedBlockGroups=" + capture.renderedBlockGroups
                            + ", renderedBlockPositions=" + capture.renderedBlockPositions
                            + ", viewportCorrections=" + capture.corrections
                            + ", scenePixelsValidated=" + capture.scenePixelsValidated);
        }
    }

    private static void validateModularUiPixels(Capture capture, NativeImage image) {
        if (capture.topLevelScissorRects.size()
                != capture.expectedModularIngredientGroups) {
            throw new IllegalStateException(
                    "Multiblocked modular UI exposed an unexpected number of native ingredient-group "
                            + "rectangles: label=" + capture.label + ", expected="
                            + capture.expectedModularIngredientGroups + ", observed="
                            + capture.topLevelScissorRects.size());
        }
        long minimumObserved = Long.MAX_VALUE;
        for (LowDragFboViewportContract.Viewport rect : capture.topLevelScissorRects) {
            long area = Math.multiplyExact((long) rect.width(), rect.height());
            long required = Math.min(16, area - 1);
            if (required < 1) {
                throw new IllegalStateException(
                        "Multiblocked ingredient-group rectangle is too small during "
                                + capture.label + ": " + rect);
            }
            long diversity = LowDragFboViewportContract.countScenePixelDiversity(
                    capture.width,
                    capture.height,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    required,
                    image::getPixelRGBA);
            if (diversity < required) {
                throw new IllegalStateException(
                        "Native Multiblocked ingredient-group viewport remained uniform: label="
                                + capture.label + ", topRect=" + rect
                                + ", differingPixels=" + diversity + ", required=" + required);
            }
            minimumObserved = Math.min(minimumObserved, diversity);
        }
        capture.modularPixelsValidated = true;
        capture.modularDiversityPixels = minimumObserved;
    }

    private static void requireExactSceneRenderer(
            Capture capture,
            String rendererClassName,
            Object renderer
    ) {
        if (capture.mode != CaptureMode.MULTIBLOCK_SCENE
                || !LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS.equals(rendererClassName)
                || capture.immediateRenderer != renderer) {
            throw new IllegalStateException(
                    "Unaudited world renderer entered capture " + capture.label
                            + ": mode=" + capture.mode + ", renderer=" + rendererClassName);
        }
    }

    private static void restoreFullCaptureViewport(
            Capture capture,
            int framebuffer,
            LowDragFboViewportContract.Viewport priorViewport,
            String source
    ) {
        RenderSystem.viewport(0, 0, capture.width, capture.height);
        LowDragFboViewportContract.Viewport corrected = currentViewport();
        LowDragFboViewportContract.Viewport expected =
                new LowDragFboViewportContract.Viewport(0, 0, capture.width, capture.height);
        if (!corrected.equals(expected)) {
            throw new IllegalStateException(
                    "LowDrag exporter viewport correction did not reach " + expected
                            + " during " + capture.label + ": actual=" + corrected);
        }
        capture.corrected(source);
        long correction = CORRECTION_COUNT.incrementAndGet();
        if (capture.auditSample) {
            ReiExportMod.LOGGER.warn(
                    "[reiexport] Corrected exact LowDrag {} window-viewport transition #{}: "
                            + "label={}, framebuffer={}, viewport={} -> {}",
                    source,
                    correction,
                    capture.label,
                    framebuffer,
                    priorViewport,
                    expected);
        }
    }

    private static void requireTargetState(
            Capture capture,
            LowDragFboViewportContract.Viewport expectedViewport,
            String boundary
    ) {
        int framebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        LowDragFboViewportContract.Viewport viewport = currentViewport();
        if (framebuffer != capture.framebuffer || !expectedViewport.equals(viewport)) {
            throw new IllegalStateException(
                    "LowDrag target state drift at " + boundary + ": label=" + capture.label
                            + ", framebuffer=" + framebuffer + " expected=" + capture.framebuffer
                            + ", viewport=" + viewport + " expected=" + expectedViewport);
        }
    }

    private static void validateMethods(List<String> failures) {
        try {
            ClassLoader loader = LowDragFboViewportCompatibility.class.getClassLoader();
            Class<?> fboType = Class.forName(
                    LowDragFboViewportContract.FBO_RENDERER_CLASS, false, loader);
            Method unbind = fboType.getDeclaredMethod("unbindFBO", int.class);
            if (unbind.getReturnType() != void.class || !Modifier.isPrivate(unbind.getModifiers())) {
                failures.add("FBOWorldSceneRenderer.unbindFBO(int) signature/modifier drift: "
                        + unbind);
            }
            Method render = fboType.getDeclaredMethod(
                    "render",
                    PoseStack.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class,
                    float.class);
            if (render.getReturnType() != void.class || !Modifier.isPublic(render.getModifiers())) {
                failures.add("FBOWorldSceneRenderer.render(PoseStack,float...) signature/modifier drift: "
                        + render);
            }

            Class<?> worldType = Class.forName(
                    LowDragFboViewportContract.WORLD_RENDERER_CLASS, false, loader);
            Method resetCamera = worldType.getDeclaredMethod("resetCamera");
            if (resetCamera.getReturnType() != void.class
                    || !Modifier.isProtected(resetCamera.getModifiers())) {
                failures.add("WorldSceneRenderer.resetCamera() signature/modifier drift: "
                        + resetCamera);
            }
            Method drawWorld = worldType.getDeclaredMethod("drawWorld");
            if (drawWorld.getReturnType() != void.class
                    || !Modifier.isProtected(drawWorld.getModifiers())) {
                failures.add("WorldSceneRenderer.drawWorld() signature/modifier drift: "
                        + drawWorld);
            }
            Field blocks = worldType.getDeclaredField("renderedBlocksMap");
            if (!Map.class.isAssignableFrom(blocks.getType())
                    || !Modifier.isPublic(blocks.getModifiers())
                    || !Modifier.isFinal(blocks.getModifiers())) {
                failures.add("WorldSceneRenderer.renderedBlocksMap field drift: " + blocks);
            }
            Field useCache = worldType.getDeclaredField("useCache");
            if (useCache.getType() != boolean.class
                    || !Modifier.isProtected(useCache.getModifiers())
                    || Modifier.isStatic(useCache.getModifiers())
                    || Modifier.isFinal(useCache.getModifiers())) {
                failures.add("WorldSceneRenderer.useCache field drift: " + useCache);
            }
            Field cacheState = worldType.getDeclaredField("cacheState");
            if (cacheState.getType() != AtomicReference.class
                    || !Modifier.isProtected(cacheState.getModifiers())
                    || Modifier.isStatic(cacheState.getModifiers())) {
                failures.add("WorldSceneRenderer.cacheState field drift: " + cacheState);
            }
            Field cacheWorker = worldType.getDeclaredField("thread");
            if (cacheWorker.getType() != Thread.class
                    || !Modifier.isProtected(cacheWorker.getModifiers())
                    || Modifier.isStatic(cacheWorker.getModifiers())) {
                failures.add("WorldSceneRenderer.thread field drift: " + cacheWorker);
            }
            if (!useCache.trySetAccessible()
                    || !cacheState.trySetAccessible()
                    || !cacheWorker.trySetAccessible()) {
                failures.add("WorldSceneRenderer cache fields are not reflectively accessible");
            }
            Class<?> cacheStateType = Class.forName(
                    LowDragFboViewportContract.WORLD_RENDERER_CACHE_STATE_CLASS,
                    false,
                    loader);
            Object[] cacheStates = cacheStateType.getEnumConstants();
            if (!cacheStateType.isEnum()
                    || cacheStates == null
                    || cacheStates.length != 4
                    || !"UNUSED".equals(((Enum<?>) cacheStates[0]).name())
                    || !"NEED".equals(((Enum<?>) cacheStates[1]).name())
                    || !"COMPILING".equals(((Enum<?>) cacheStates[2]).name())
                    || !"COMPILED".equals(((Enum<?>) cacheStates[3]).name())) {
                failures.add("WorldSceneRenderer.CacheState enum drift: " + cacheStateType);
            }

            Class<?> positionedRectType = Class.forName(
                    LowDragFboViewportContract.POSITIONED_RECT_CLASS, false, loader);
            Constructor<?> constructor = positionedRectType.getConstructor(
                    int.class, int.class, int.class, int.class);
            if (!Modifier.isPublic(constructor.getModifiers())) {
                failures.add("PositionedRect(int,int,int,int) constructor modifier drift: "
                        + constructor);
            }

            Class<?> immediateType = Class.forName(
                    LowDragFboViewportContract.IMMEDIATE_RENDERER_CLASS, false, loader);
            if (immediateType.getSuperclass() != worldType
                    || Modifier.isAbstract(immediateType.getModifiers())) {
                failures.add("ImmediateWorldSceneRenderer inheritance/modifier drift: "
                        + immediateType);
            }
            Method positionedRect = immediateType.getDeclaredMethod(
                    "getPositionedRect",
                    int.class, int.class, int.class, int.class);
            if (positionedRect.getReturnType() != positionedRectType
                    || !Modifier.isProtected(positionedRect.getModifiers())) {
                failures.add("ImmediateWorldSceneRenderer.getPositionedRect(int...) drift: "
                        + positionedRect);
            }

            Class<?> renderUtilsType = Class.forName(
                    LowDragFboViewportContract.RENDER_UTILS_CLASS, false, loader);
            Method peek = renderUtilsType.getDeclaredMethod("peekFirstScissorOrFullScreen");
            if (peek.getReturnType() != int[].class
                    || !Modifier.isPrivate(peek.getModifiers())
                    || !Modifier.isStatic(peek.getModifiers())) {
                failures.add("RenderUtils.peekFirstScissorOrFullScreen() drift: " + peek);
            }
            Method apply = renderUtilsType.getDeclaredMethod(
                    "applyScissor", int.class, int.class, int.class, int.class);
            if (apply.getReturnType() != void.class
                    || !Modifier.isPrivate(apply.getModifiers())
                    || !Modifier.isStatic(apply.getModifiers())) {
                failures.add("RenderUtils.applyScissor(int...) drift: " + apply);
            }

            positionedRectConstructor = constructor;
            renderedBlocksMapField = blocks;
            sceneUseCacheField = useCache;
            sceneCacheStateField = cacheState;
            sceneCacheWorkerField = cacheWorker;
        } catch (ReflectiveOperationException | LinkageError exception) {
            failures.add("LowDrag MM2 method validation failed: "
                    + exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    private static void validateClassResource(
            String resourcePath,
            String expectedSha256,
            List<String> failures
    ) {
        try (InputStream input = LowDragFboViewportCompatibility.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (input == null) {
                failures.add("missing class resource=" + resourcePath);
                return;
            }
            String actual = sha256(input);
            if (!expectedSha256.equals(actual)) {
                failures.add("class bytecode drift resource=" + resourcePath
                        + ", expectedSha256=" + expectedSha256
                        + ", actualSha256=" + actual);
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            failures.add("class bytecode validation failed resource=" + resourcePath
                    + ", exception=" + exception.getClass().getName()
                    + ": " + exception.getMessage());
        }
    }

    private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }

    private static LowDragFboViewportContract.Viewport currentViewport() {
        IntBuffer query = VIEWPORT_QUERY.get();
        query.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, query);
        return new LowDragFboViewportContract.Viewport(
                query.get(0), query.get(1), query.get(2), query.get(3));
    }

    private static LowDragFboViewportContract.Viewport windowViewport() {
        Minecraft minecraft = Minecraft.getInstance();
        return new LowDragFboViewportContract.Viewport(
                0,
                0,
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight());
    }
}
