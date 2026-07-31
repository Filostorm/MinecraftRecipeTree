package com.recipetree.reiexport118;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.recipetree.reiexport118.compat.LowDragFboViewportCompatibility;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClock;
import com.recipetree.reiexport118.compat.Mm2LightmapReadiness;
import com.recipetree.reiexport118.compat.Mm2SpiritEntityRenderDeterminism;
import com.recipetree.reiexport118.compat.Mm2UnattendedUiScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class OffscreenRenderer implements AutoCloseable {
    private static final int ORIENTATION_SIZE = 8;
    private static final int ORIENTATION_RED = 0xffff0000;
    private static final int ORIENTATION_GREEN = 0xff00ff00;
    private static final int ORIENTATION_BLUE = 0xff0000ff;
    private static final int ORIENTATION_YELLOW = 0xffffff00;
    private static final int CULL_CALIBRATION_WIDTH = 8;
    private static final int CULL_CALIBRATION_HEIGHT = 4;
    private static final int CULL_CALIBRATION_HALF_WIDTH = CULL_CALIBRATION_WIDTH / 2;
    private static final int CULL_CALIBRATION_COLOR = 0x80ffffff;

    private RenderTarget target;
    private int width = -1;
    private int height = -1;
    private Matrix4f savedProjection;
    private boolean modelViewPushed;
    private boolean targetBound;
    private boolean lowDragCaptureScopeActive;
    private boolean captureActive;

    NativeImage capture(int pixelWidth, int pixelHeight, Consumer<PoseStack> draw) {
        return capture(pixelWidth, pixelHeight, 0x00000000, draw);
    }

    /**
     * Executes a four-corner native-render/readback calibration. This makes orientation drift a
     * startup failure instead of allowing an entire stress export to publish mirrored assets.
     */
    void validateReadbackOrientation() {
        try (NativeImage image = capture(
                ORIENTATION_SIZE,
                ORIENTATION_SIZE,
                0xff000000,
                pose -> {
                    GuiComponent.fill(pose, 0, 0, 4, 4, ORIENTATION_RED);
                    GuiComponent.fill(pose, 4, 0, 8, 4, ORIENTATION_GREEN);
                    GuiComponent.fill(pose, 0, 4, 4, 8, ORIENTATION_BLUE);
                    GuiComponent.fill(pose, 4, 4, 8, 8, ORIENTATION_YELLOW);
                }
        )) {
            requireCalibrationPixel(image, 1, 1, ORIENTATION_RED, "top-left");
            requireCalibrationPixel(image, 6, 1, ORIENTATION_GREEN, "top-right");
            requireCalibrationPixel(image, 1, 6, ORIENTATION_BLUE, "bottom-left");
            requireCalibrationPixel(image, 6, 6, ORIENTATION_YELLOW, "bottom-right");
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Native offscreen readback orientation calibration passed: "
                        + "top-left=red, top-right=green, bottom-left=blue, bottom-right=yellow; "
                        + "no vertical or horizontal pixel transform is active"
        );
    }

    /**
     * Verifies the global cull-enabled invariant required by Minecraft's translucent item sheet.
     * Its {@code CullStateShard(true)} intentionally performs no setup call, so a no-cull GUI
     * baseline renders both faces of a generated translucent item and composites its sprite twice.
     *
     * <p>The left half is one translucent reference quad with culling explicitly disabled. The
     * right half contains the same quad twice with opposite winding after restoring the exporter
     * cull baseline. Correct back-face culling leaves exactly one right-hand contribution, making
     * both halves pixel-identical. This calibration runs once per claimed export and performs no
     * per-item GL query or fallback.</p>
     */
    void validateTranslucentCullBaseline() {
        try (NativeImage image = capture(
                CULL_CALIBRATION_WIDTH,
                CULL_CALIBRATION_HEIGHT,
                0x00000000,
                pose -> {
                    RenderSystem.disableCull();
                    GL11.glDisable(GL11.GL_CULL_FACE);
                    drawCullCalibrationQuads(
                            pose, 0, CULL_CALIBRATION_HALF_WIDTH, false);

                    establishGuiBaseline();
                    drawCullCalibrationQuads(
                            pose,
                            CULL_CALIBRATION_HALF_WIDTH,
                            CULL_CALIBRATION_WIDTH,
                            true);
                }
        )) {
            int visibleReferencePixels = 0;
            for (int y = 0; y < CULL_CALIBRATION_HEIGHT; y++) {
                for (int x = 0; x < CULL_CALIBRATION_HALF_WIDTH; x++) {
                    int reference = image.getPixelRGBA(x, y);
                    int audited = image.getPixelRGBA(x + CULL_CALIBRATION_HALF_WIDTH, y);
                    if (NativeImage.getA(reference) != 0) {
                        visibleReferencePixels++;
                    }
                    if (reference != audited) {
                        throw new IllegalStateException(
                                "Native translucent-item cull calibration failed at (" + x + "," + y
                                        + "): referenceAbgr=0x" + Integer.toHexString(reference)
                                        + ", oppositeWindingAbgr=0x" + Integer.toHexString(audited)
                                        + ". Both faces may have blended; no fallback or publication "
                                        + "was attempted."
                        );
                    }
                }
            }
            int expectedVisiblePixels =
                    CULL_CALIBRATION_HALF_WIDTH * CULL_CALIBRATION_HEIGHT;
            if (visibleReferencePixels != expectedVisiblePixels) {
                throw new IllegalStateException(
                        "Native translucent-item cull calibration produced an incomplete reference: "
                                + "visiblePixels=" + visibleReferencePixels + ", expected="
                                + expectedVisiblePixels + ". No fallback or publication was attempted."
                );
            }
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] Native translucent-item cull calibration passed: one authored "
                        + "contribution survived from two opposite-winding quads; generated "
                        + "front/back sprite overdraw is disabled"
        );
    }

    private static void drawCullCalibrationQuads(
            PoseStack pose,
            int left,
            int right,
            boolean includeOppositeWinding
    ) {
        Matrix4f matrix = pose.last().pose();
        float alpha = ((CULL_CALIBRATION_COLOR >>> 24) & 0xff) / 255.0f;
        float red = ((CULL_CALIBRATION_COLOR >>> 16) & 0xff) / 255.0f;
        float green = ((CULL_CALIBRATION_COLOR >>> 8) & 0xff) / 255.0f;
        float blue = (CULL_CALIBRATION_COLOR & 0xff) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        calibrationVertex(buffer, matrix, left, CULL_CALIBRATION_HEIGHT, red, green, blue, alpha);
        calibrationVertex(buffer, matrix, right, CULL_CALIBRATION_HEIGHT, red, green, blue, alpha);
        calibrationVertex(buffer, matrix, right, 0, red, green, blue, alpha);
        calibrationVertex(buffer, matrix, left, 0, red, green, blue, alpha);
        if (includeOppositeWinding) {
            calibrationVertex(buffer, matrix, left, 0, red, green, blue, alpha);
            calibrationVertex(buffer, matrix, right, 0, red, green, blue, alpha);
            calibrationVertex(buffer, matrix, right, CULL_CALIBRATION_HEIGHT, red, green, blue, alpha);
            calibrationVertex(buffer, matrix, left, CULL_CALIBRATION_HEIGHT, red, green, blue, alpha);
        }
        buffer.end();
        BufferUploader.end(buffer);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void calibrationVertex(
            BufferBuilder buffer,
            Matrix4f matrix,
            int x,
            int y,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        buffer.vertex(matrix, x, y, 0.0f)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    /**
     * Captures native GUI rendering over an attachment clear color. An attachment clear is both
     * cheaper and more correct than a full-canvas GUI quad: a quad writes coplanar GUI depth and
     * can occlude category panels, slots, and arrows rendered at the same depth.
     */
    NativeImage capture(int pixelWidth, int pixelHeight, int clearArgb, Consumer<PoseStack> draw) {
        return capture(pixelWidth, pixelHeight, clearArgb, null, null, false, 0, null, draw);
    }

    NativeImage capture(
            int pixelWidth,
            int pixelHeight,
            int clearArgb,
            LowDragFboViewportCompatibility.CaptureMode lowDragCaptureMode,
            String lowDragCaptureLabel,
            boolean verifyExternalTargetState,
            int expectedModularIngredientGroups,
            Supplier<? extends AutoCloseable> nativeContextFactory,
            Consumer<PoseStack> draw
    ) {
        if (captureActive) {
            throw new IllegalStateException("Nested offscreen capture is not supported.");
        }
        captureActive = true;
        String captureLabel = lowDragCaptureLabel == null
                ? pixelWidth + "x" + pixelHeight
                : lowDragCaptureLabel;
        NativeImage captured = null;
        try {
            Mm2UnattendedUiScope.requireCaptureBaseline(captureLabel);
            Mm2LightmapReadiness.requireCaptureBaseline(captureLabel);
        } catch (Throwable throwable) {
            captureActive = false;
            throw unchecked(throwable);
        }
        try (AutoCloseable ignoredNativeContext = nativeContextFactory == null
                     ? null : nativeContextFactory.get();
             Mm2OffscreenGlintClock.CaptureScope ignoredGlint =
                     Mm2OffscreenGlintClock.beginOffscreenCapture(captureLabel);
             Mm2SpiritEntityRenderDeterminism.CaptureScope ignoredSpirit =
                     Mm2SpiritEntityRenderDeterminism.beginNativeCapture(captureLabel)) {
            captured = captureOwnedTarget(
                    pixelWidth,
                    pixelHeight,
                    clearArgb,
                    lowDragCaptureMode,
                    lowDragCaptureLabel,
                    verifyExternalTargetState,
                    expectedModularIngredientGroups,
                    draw);
            return captured;
        } catch (Throwable throwable) {
            if (captured != null) {
                captured.close();
            }
            throw unchecked(throwable);
        } finally {
            captureActive = false;
        }
    }

    private NativeImage captureOwnedTarget(
            int pixelWidth,
            int pixelHeight,
            int clearArgb,
            LowDragFboViewportCompatibility.CaptureMode lowDragCaptureMode,
            String lowDragCaptureLabel,
            boolean verifyExternalTargetState,
            int expectedModularIngredientGroups,
            Consumer<PoseStack> draw
    ) {
        NativeImage image = null;
        Throwable primaryFailure = null;
        try {
            PoseStack pose = begin(
                    pixelWidth,
                    pixelHeight,
                    clearArgb,
                    lowDragCaptureMode,
                    lowDragCaptureLabel,
                    verifyExternalTargetState,
                    expectedModularIngredientGroups
            );
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            draw.accept(pose);

            // A sampled recipe capture verifies that third-party widgets returned to the target.
            // Every capture then rebinds deterministically before flushing deferred item quads;
            // this avoids two synchronous GL state-query round trips for every catalog entry.
            if (verifyExternalTargetState) {
                verifyTargetStateAfterExternalDraw();
            }
            target.bindWrite(true);
            targetBound = true;
            establishGuiBaseline();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();

            image = new NativeImage(width, height, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            if (lowDragCaptureScopeActive) {
                LowDragFboViewportCompatibility.validateCapturedImage(image);
            }
            // The exporter projection is already Y-down, matching NativeImage/PNG row order.
            // Flipping the readback here vertically inverted every item and recipe while leaving
            // symmetric sprites deceptively plausible. Preserve the native texture rows exactly.
            return image;
        } catch (Throwable throwable) {
            primaryFailure = throwable;
            if (image != null) {
                image.close();
            }
            throw unchecked(throwable);
        } finally {
            try {
                restore();
            } catch (Throwable restoreFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(restoreFailure);
                } else {
                    if (image != null) {
                        image.close();
                    }
                    throw unchecked(restoreFailure);
                }
            }
        }
    }

    private static RuntimeException unchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Unexpected checked failure during offscreen capture", throwable);
    }

    private static void requireCalibrationPixel(
            NativeImage image,
            int x,
            int y,
            int expectedArgb,
            String corner
    ) {
        int pixel = image.getPixelRGBA(x, y);
        int actualArgb = (NativeImage.getA(pixel) << 24)
                | (NativeImage.getR(pixel) << 16)
                | (NativeImage.getG(pixel) << 8)
                | NativeImage.getB(pixel);
        if (actualArgb != expectedArgb) {
            throw new IllegalStateException(
                    "Native offscreen readback orientation calibration failed at " + corner
                            + " (" + x + "," + y + "): expectedArgb=0x"
                            + Integer.toHexString(expectedArgb) + ", actualArgb=0x"
                            + Integer.toHexString(actualArgb)
                            + ". No image-axis fallback was attempted."
            );
        }
    }

    private PoseStack begin(
            int pixelWidth,
            int pixelHeight,
            int clearArgb,
            LowDragFboViewportCompatibility.CaptureMode lowDragCaptureMode,
            String lowDragCaptureLabel,
            boolean auditSample,
            int expectedModularIngredientGroups
    ) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Offscreen rendering must run on Minecraft's render thread.");
        }
        if (pixelWidth < 1 || pixelHeight < 1 || pixelWidth > 4096 || pixelHeight > 4096) {
            throw new IllegalArgumentException("Invalid offscreen target " + pixelWidth + "x" + pixelHeight);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || width != pixelWidth || height != pixelHeight) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(pixelWidth, pixelHeight, true, Minecraft.ON_OSX);
            width = pixelWidth;
            height = pixelHeight;
        }

        // LowDragLib category icons can exit with depthMask(false) and depth testing disabled.
        // Normalize both RenderSystem's cache and the driver before clear so stale masks cannot
        // turn a valid color/depth clear into a transparent or uncleared capture.
        establishGuiBaseline();
        target.setClearColor(
                ((clearArgb >>> 16) & 0xff) / 255.0f,
                ((clearArgb >>> 8) & 0xff) / 255.0f,
                (clearArgb & 0xff) / 255.0f,
                ((clearArgb >>> 24) & 0xff) / 255.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        targetBound = true;
        if (lowDragCaptureMode != null) {
            lowDragCaptureScopeActive = LowDragFboViewportCompatibility.beginExporterCapture(
                    target.frameBufferId,
                    width,
                    height,
                    lowDragCaptureMode,
                    lowDragCaptureLabel,
                    auditSample,
                    expectedModularIngredientGroups
            );
        }

        savedProjection = RenderSystem.getProjectionMatrix().copy();
        RenderSystem.setProjectionMatrix(Matrix4f.orthographic(
                0.0f, pixelWidth, pixelHeight, 0.0f, 1000.0f, 3000.0f));
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelViewPushed = true;
        modelView.setIdentity();
        modelView.translate(0.0d, 0.0d, -2000.0d);
        RenderSystem.applyModelViewMatrix();
        establishGuiBaseline();
        Lighting.setupFor3DItems();
        return new PoseStack();
    }

    private void verifyTargetStateAfterExternalDraw() {
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewportX = viewport[0];
        int viewportY = viewport[1];
        int viewportWidth = viewport[2];
        int viewportHeight = viewport[3];
        boolean drifted = drawFramebuffer != target.frameBufferId
                || viewportX != 0 || viewportY != 0
                || viewportWidth != width || viewportHeight != height;
        if (drifted) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Sampled recipe capture leaked offscreen target state after " +
                            "third-party rendering: drawFramebuffer={} expected={}, viewport={},{},{}x{} " +
                            "expected=0,0,{}x{}. Pixels drawn after the leak cannot be reconstructed; " +
                            "publication is rejected.",
                    drawFramebuffer, target.frameBufferId,
                    viewportX, viewportY, viewportWidth, viewportHeight, width, height
            );
            throw new IllegalStateException(
                    "Third-party recipe renderer leaked the exporter framebuffer or viewport"
            );
        }
    }

    /**
     * Establishes a deterministic GUI baseline through RenderSystem so its state cache and the
     * actual driver stay synchronized. GL_LEQUAL is also forced once through the driver because
     * third-party raw GL calls can desynchronize RenderSystem's cached depth function.
     */
    private static void establishGuiBaseline() {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Minecraft's default CULL RenderStateShard is deliberately a no-op. Keep both
        // RenderSystem's cache and the OpenGL driver at that required global default so flat
        // generated item models do not blend their translucent front and back faces together.
        // This target's Y-down GUI projection maps authored GUI quads to clockwise window-space
        // winding; GUI ItemRenderer's negative-Y transform does the same for model front faces.
        RenderSystem.enableCull();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CW);
        RenderSystem.disableScissor();
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void restore() {
        try {
            establishGuiBaseline();
            if (modelViewPushed) {
                PoseStack modelView = RenderSystem.getModelViewStack();
                modelView.popPose();
                modelViewPushed = false;
                RenderSystem.applyModelViewMatrix();
            }
            if (savedProjection != null) {
                RenderSystem.setProjectionMatrix(savedProjection);
                savedProjection = null;
            }
            // The exporter uses clockwise front faces only while its reversed-Y projection is
            // active. Minecraft's restored world/GUI projection uses the OpenGL default CCW
            // convention. Reset directly instead of issuing a synchronous per-capture GL query.
            GL11.glFrontFace(GL11.GL_CCW);
            if (targetBound) {
                target.unbindWrite();
                targetBound = false;
                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            }
        } finally {
            if (lowDragCaptureScopeActive) {
                lowDragCaptureScopeActive = false;
                LowDragFboViewportCompatibility.endExporterCapture();
            }
        }
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
            width = -1;
            height = -1;
        }
    }
}
