package com.recipetree.jeiexport112;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.IntBuffer;

/** Client-thread framebuffer renderer with direct GL readback into a Java BufferedImage. */
final class OffscreenRenderer implements Closeable {
    private static final int MAX_GL_ERRORS_PER_DRAIN = 64;
    static final int INTEGER_QUERY_BUFFER_CAPACITY = 16;

    interface DrawCall {
        void draw(Minecraft minecraft) throws Exception;
    }

    private final Minecraft minecraft = Minecraft.getMinecraft();
    private Framebuffer framebuffer;
    private int width;
    private int height;
    private IntBuffer pixels;
    /*
     * LWJGL 2's glGetInteger(int, IntBuffer) binding conservatively requires room for the
     * largest integer query (16 values), even though GL_VIEWPORT returns only four. A four-value
     * buffer fails in BufferChecks before OpenGL is called.
     */
    private final IntBuffer viewport = BufferUtils.createIntBuffer(INTEGER_QUERY_BUFFER_CAPACITY);
    private final int maxTextureSize;
    private long clearedStaleGlErrors;

    OffscreenRenderer() {
        maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("OpenGL reported an invalid GL_MAX_TEXTURE_SIZE=" + maxTextureSize);
        }
        JeiExportMod.LOGGER.info(
                "[jeiexport] OpenGL GL_MAX_TEXTURE_SIZE={}; native offscreen draws use an " +
                        "exact texture-unit/cache baseline before renderer-owned texture binds",
                maxTextureSize);
    }

    int getMaxTextureSize() {
        return maxTextureSize;
    }

    BufferedImage render(int targetWidth, int targetHeight, DrawCall drawCall) throws Exception {
        return render(targetWidth, targetHeight, 0x00000000, drawCall);
    }

    /**
     * Renders onto a color-and-depth buffer cleared to {@code clearArgb}. Clearing the color
     * attachment is both cheaper and more correct than drawing a full-size GUI quad: a quad at
     * z=0 writes the depth buffer and can occlude HEI's border and category-background quads,
     * which are intentionally rendered at that same GUI depth.
     */
    BufferedImage render(int targetWidth, int targetHeight, int clearArgb, DrawCall drawCall)
            throws Exception {
        if (targetWidth <= 0 || targetHeight <= 0 ||
                targetWidth > maxTextureSize || targetHeight > maxTextureSize) {
            throw new IOException("Invalid offscreen target " + targetWidth + "x" + targetHeight);
        }
        Buffer viewportState = viewport;
        viewportState.clear();
        viewportState.limit(INTEGER_QUERY_BUFFER_CAPACITY);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int oldX = viewport.get(0);
        int oldY = viewport.get(1);
        int oldWidth = viewport.get(2);
        int oldHeight = viewport.get(3);
        int oldFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int oldPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int oldPackRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int oldPackSkipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int oldPackSkipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int oldPackSwapBytes = GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES);

        BufferedImage image = null;
        Throwable failure = null;
        boolean projectionPushed = false;
        boolean modelViewPushed = false;
        try {
            ensureFramebuffer(targetWidth, targetHeight);
            framebuffer.setFramebufferColor(
                    ((clearArgb >>> 16) & 0xff) / 255.0F,
                    ((clearArgb >>> 8) & 0xff) / 255.0F,
                    (clearArgb & 0xff) / 255.0F,
                    ((clearArgb >>> 24) & 0xff) / 255.0F);
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(true);
            establishGuiBaseline();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            projectionPushed = true;
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, targetWidth, targetHeight, 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            modelViewPushed = true;
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            drawCall.draw(minecraft);
            image = readPixels(targetWidth, targetHeight);
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            if (modelViewPushed) {
                try {
                    GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                    GlStateManager.popMatrix();
                } catch (Throwable restorationFailure) {
                    failure = mergeFailures(failure, restorationFailure);
                }
            }
            if (projectionPushed) {
                try {
                    GlStateManager.matrixMode(GL11.GL_PROJECTION);
                    GlStateManager.popMatrix();
                } catch (Throwable restorationFailure) {
                    failure = mergeFailures(failure, restorationFailure);
                }
            }
            try {
                establishGuiBaseline();
            } catch (Throwable restorationFailure) {
                failure = mergeFailures(failure, restorationFailure);
            }
            try {
                GlStateManager.matrixMode(oldMatrixMode);
            } catch (Throwable restorationFailure) {
                failure = mergeFailures(failure, restorationFailure);
            }
            try {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, oldFramebuffer);
            } catch (Throwable restorationFailure) {
                failure = mergeFailures(failure, restorationFailure);
            }
            failure = restorePixelStore(
                    failure, GL11.GL_PACK_SWAP_BYTES, oldPackSwapBytes, "GL_PACK_SWAP_BYTES");
            failure = restorePixelStore(
                    failure, GL11.GL_PACK_SKIP_PIXELS, oldPackSkipPixels, "GL_PACK_SKIP_PIXELS");
            failure = restorePixelStore(
                    failure, GL11.GL_PACK_SKIP_ROWS, oldPackSkipRows, "GL_PACK_SKIP_ROWS");
            failure = restorePixelStore(
                    failure, GL11.GL_PACK_ROW_LENGTH, oldPackRowLength, "GL_PACK_ROW_LENGTH");
            failure = restorePixelStore(
                    failure, GL11.GL_PACK_ALIGNMENT, oldPackAlignment, "GL_PACK_ALIGNMENT");
            try {
                GL11.glViewport(oldX, oldY, oldWidth, oldHeight);
            } catch (Throwable restorationFailure) {
                failure = mergeFailures(failure, restorationFailure);
            }
        }
        if (failure != null) {
            FatalErrors.rethrowIfFatal(failure);
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            throw new IOException("Offscreen render failed", failure);
        }
        return image;
    }

    /**
     * Establishes a deterministic GUI state through GlStateManager so its cache and the real
     * OpenGL state remain synchronized. Raw glPushAttrib/glPopAttrib must not be used on 1.12.2:
     * it restores driver state behind GlStateManager's cache and corrupts later texture/state binds.
     */
    private static void establishGuiBaseline() {
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        /*
         * A recovered LWJGL Display context, or a mod's raw glActiveTexture call, can leave the
         * driver active unit out of sync with GlStateManager.activeTextureUnit. Force the driver
         * to the same lightmap unit selected in the cache before synchronizing its capability.
         */
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        /*
         * Vanilla RenderItem binds TextureMap.LOCATION_BLOCKS_TEXTURE through TextureManager,
         * which delegates to GlStateManager.bindTexture. That method intentionally skips
         * glBindTexture when its cached ID already matches. Resetting both cache and driver to
         * texture 0 guarantees the native renderer's nonzero atlas ID takes the real bind path;
         * the exporter never substitutes atlas pixels or fabricates an icon.
         */
        GlStateManager.bindTexture(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        /*
         * HEI draws its outer border and category background (slots and arrow) as
         * coplanar GUI quads. Mods may change the driver depth function through raw
         * OpenGL, leaving GlStateManager's cached value at GL_LEQUAL; in that case the
         * cached call above becomes a no-op while the driver remains at GL_LESS and
         * rejects HEI's second, equal-depth quad. Force the real state once per render
         * after synchronizing the cache. This avoids a synchronous glGet query in the
         * hot path and preserves depth testing inside 3D item renders.
         */
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.disablePolygonOffset();
        GlStateManager.disableColorLogic();
        GlStateManager.disableNormalize();
        GlStateManager.disableRescaleNormal();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    private static Throwable mergeFailures(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (!FatalErrors.isFatal(primary) && FatalErrors.isFatal(additional)) {
            if (additional != primary) {
                additional.addSuppressed(primary);
            }
            return additional;
        }
        if (additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private void ensureFramebuffer(int targetWidth, int targetHeight) {
        if (framebuffer != null && width == targetWidth && height == targetHeight) {
            return;
        }
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
        framebuffer = new Framebuffer(targetWidth, targetHeight, true);
        width = targetWidth;
        height = targetHeight;
    }

    private BufferedImage readPixels(int targetWidth, int targetHeight) throws IOException {
        int pixelCount = Math.multiplyExact(targetWidth, targetHeight);
        if (pixels == null || pixels.capacity() < pixelCount) {
            pixels = BufferUtils.createIntBuffer(pixelCount);
        }
        Buffer pixelState = pixels;
        pixelState.clear();
        pixelState.limit(pixelCount);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES, GL11.GL_FALSE);

        GlErrorBatch staleErrors = drainGlErrors();
        if (!staleErrors.isEmpty()) {
            clearedStaleGlErrors += staleErrors.count;
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Cleared {} stale OpenGL error(s) immediately before glReadPixels; " +
                            "codes={}, cumulativeCleared={}",
                    staleErrors.count, staleErrors.describe(), clearedStaleGlErrors);
            if (staleErrors.limitReached) {
                throw new IOException("OpenGL continued reporting errors after " +
                        MAX_GL_ERRORS_PER_DRAIN + " glGetError calls before glReadPixels; refusing readback");
            }
        }
        GL11.glReadPixels(0, 0, targetWidth, targetHeight,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        GlErrorBatch readErrors = drainGlErrors();
        if (!readErrors.isEmpty()) {
            throw new IOException("glReadPixels reported " + readErrors.count +
                    " OpenGL error(s): " + readErrors.describe() +
                    "; refusing to publish potentially stale pixel data");
        }

        BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        PixelReadback.copyBgraRevBottomUpToArgbTopDown(
                pixels, targetWidth, targetHeight, argb);
        return image;
    }

    private static Throwable restorePixelStore(
            Throwable primary, int parameter, int value, String name) {
        try {
            GL11.glPixelStorei(parameter, value);
            GlErrorBatch errors = drainGlErrors();
            if (!errors.isEmpty()) {
                throw new IOException("Restoring " + name + "=" + value + " produced " +
                        errors.count + " OpenGL error(s): " + errors.describe());
            }
        } catch (Throwable restorationFailure) {
            return mergeFailures(primary, restorationFailure);
        }
        return primary;
    }

    private static GlErrorBatch drainGlErrors() {
        GlErrorBatch errors = new GlErrorBatch();
        for (int index = 0; index < MAX_GL_ERRORS_PER_DRAIN; index++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                return errors;
            }
            errors.add(error);
        }
        errors.limitReached = true;
        return errors;
    }

    private static String glErrorName(int error) {
        switch (error) {
            case GL11.GL_INVALID_ENUM:
                return "GL_INVALID_ENUM";
            case GL11.GL_INVALID_VALUE:
                return "GL_INVALID_VALUE";
            case GL11.GL_INVALID_OPERATION:
                return "GL_INVALID_OPERATION";
            case GL11.GL_STACK_OVERFLOW:
                return "GL_STACK_OVERFLOW";
            case GL11.GL_STACK_UNDERFLOW:
                return "GL_STACK_UNDERFLOW";
            case GL11.GL_OUT_OF_MEMORY:
                return "GL_OUT_OF_MEMORY";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";
            default:
                return "UNKNOWN_GL_ERROR";
        }
    }

    private static final class GlErrorBatch {
        int count;
        boolean limitReached;
        private final StringBuilder description = new StringBuilder();

        void add(int error) {
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(glErrorName(error)).append("(0x")
                    .append(Integer.toHexString(error)).append(')');
            count++;
        }

        boolean isEmpty() {
            return count == 0;
        }

        String describe() {
            return description.toString();
        }
    }

    @Override
    public void close() {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
        pixels = null;
    }
}
