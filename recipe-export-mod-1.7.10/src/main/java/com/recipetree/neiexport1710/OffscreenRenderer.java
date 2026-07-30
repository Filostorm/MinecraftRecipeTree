package com.recipetree.neiexport1710;

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

/** Reusable client-thread framebuffer. All Minecraft/NEI drawing remains on the GL owner thread. */
final class OffscreenRenderer implements Closeable {
    interface DrawCall {
        void draw() throws Exception;
    }

    private static final int INTEGER_QUERY_CAPACITY = 16;
    private static final int MAX_GL_ERRORS = 64;

    private final int maxTextureSize;
    private final IntBuffer viewport = BufferUtils.createIntBuffer(INTEGER_QUERY_CAPACITY);
    private Framebuffer framebuffer;
    private IntBuffer pixels;
    private int width;
    private int height;

    OffscreenRenderer() {
        maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("OpenGL reported invalid GL_MAX_TEXTURE_SIZE=" + maxTextureSize);
        }
        GtnhNeiExportMod.LOGGER.info("[gtnh-nei-export] GL_MAX_TEXTURE_SIZE={}", maxTextureSize);
    }

    int maxTextureSize() {
        return maxTextureSize;
    }

    BufferedImage render(int targetWidth, int targetHeight, int clearArgb, DrawCall drawCall) throws Exception {
        return renderScaled(targetWidth, targetHeight, 1, clearArgb, drawCall);
    }

    BufferedImage renderScaled(
            int logicalWidth,
            int logicalHeight,
            int scale,
            int clearArgb,
            DrawCall drawCall) throws Exception {
        if (logicalWidth <= 0 || logicalHeight <= 0 || scale <= 0) {
            throw new IOException("Invalid scaled offscreen target logical="
                    + logicalWidth + "x" + logicalHeight + " scale=" + scale);
        }

        final int targetWidth;
        final int targetHeight;
        try {
            targetWidth = Math.multiplyExact(logicalWidth, scale);
            targetHeight = Math.multiplyExact(logicalHeight, scale);
        } catch (ArithmeticException overflow) {
            throw new IOException("Scaled offscreen target dimensions overflow logical="
                    + logicalWidth + "x" + logicalHeight + " scale=" + scale, overflow);
        }
        if (targetWidth > maxTextureSize || targetHeight > maxTextureSize) {
            throw new IOException("Invalid offscreen target " + targetWidth + "x" + targetHeight
                    + " (GL_MAX_TEXTURE_SIZE=" + maxTextureSize + ")");
        }

        Buffer viewportState = viewport;
        viewportState.clear();
        viewportState.limit(INTEGER_QUERY_CAPACITY);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int oldX = viewport.get(0);
        int oldY = viewport.get(1);
        int oldWidth = viewport.get(2);
        int oldHeight = viewport.get(3);
        int oldFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int oldMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        Throwable failure = null;
        BufferedImage image = null;
        boolean attributesPushed = false;
        boolean clientAttributesPushed = false;
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

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attributesPushed = true;
            GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
            clientAttributesPushed = true;
            establishGuiState();

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glLoadIdentity();
            // Keep the GUI coordinate system stable while increasing raster density.
            // Several GTNH mods own custom inventory renderers that assume a 16x16
            // logical viewport; changing their coordinates would alter or clip output.
            GL11.glOrtho(0.0D, logicalWidth, logicalHeight, 0.0D, 1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelViewPushed = true;
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

            drawCall.draw();
            image = readPixels(targetWidth, targetHeight);
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (modelViewPushed) {
                try {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            if (projectionPushed) {
                try {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            if (clientAttributesPushed) {
                try {
                    GL11.glPopClientAttrib();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            if (attributesPushed) {
                try {
                    GL11.glPopAttrib();
                } catch (Throwable restore) {
                    failure = merge(failure, restore);
                }
            }
            try {
                GL11.glMatrixMode(oldMatrixMode);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFramebuffer);
                GL11.glViewport(oldX, oldY, oldWidth, oldHeight);
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
        }

        if (failure != null) {
            FatalErrors.rethrowIfFatal(failure);
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw new IOException("Offscreen rendering failed", failure);
        }
        return image;
    }

    private static void establishGuiState() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColorMask(true, true, true, true);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.disableStandardItemLighting();
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
        Buffer state = pixels;
        state.clear();
        state.limit(pixelCount);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        drainErrors("before glReadPixels", false);
        GL11.glReadPixels(0, 0, targetWidth, targetHeight,
                GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        drainErrors("after glReadPixels", true);

        BufferedImage image = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        int[] destination = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        PixelReadback.copyBgraRevBottomUpToArgbTopDown(pixels, targetWidth, targetHeight, destination);
        return image;
    }

    private static void drainErrors(String stage, boolean failOnAny) throws IOException {
        StringBuilder codes = new StringBuilder();
        int count = 0;
        for (; count < MAX_GL_ERRORS; count++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                if (count > 0) {
                    if (failOnAny) {
                        throw new IOException(stage + " reported GL errors: " + codes);
                    }
                    GtnhNeiExportMod.LOGGER.warn(
                            "[gtnh-nei-export] Cleared {} stale GL errors {}", count, codes);
                }
                return;
            }
            if (codes.length() > 0) {
                codes.append(',');
            }
            codes.append("0x").append(Integer.toHexString(error));
        }
        throw new IOException(stage + " did not reach GL_NO_ERROR after " + MAX_GL_ERRORS
                + " reads; codes=" + codes);
    }

    private static Throwable merge(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    @Override
    public void close() {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }
}
