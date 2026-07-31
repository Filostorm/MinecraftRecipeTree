package com.recipetree.jeiexport;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Renders GUI-style content (items, recipe layouts, entities) into an offscreen
 * framebuffer with a transparent background, then reads it back as a {@link NativeImage}.
 *
 * <p>Uses the same orthographic projection vanilla uses for GUI rendering, so everything
 * (3D blocks in their isometric GUI pose, flat items, BEWLR special renderers, enchantment
 * glint) looks exactly like it does in-game.</p>
 */
public final class OffscreenRenderer implements AutoCloseable {
    private RenderTarget target;
    private int width = -1;
    private int height = -1;

    private Matrix4f savedProjection;
    private VertexSorting savedSorting;
    private boolean captureActive;
    private boolean modelViewPushed;
    private boolean targetBound;

    /**
     * Runs one isolated offscreen capture. Render state is restored even when a modded
     * ingredient, recipe, or entity renderer throws.
     */
    public NativeImage capture(int pixelWidth, int pixelHeight, Consumer<GuiGraphics> draw) {
        Objects.requireNonNull(draw, "draw");
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("Capture dimensions must be positive: "
                    + pixelWidth + "x" + pixelHeight);
        }
        if (captureActive) {
            throw new IllegalStateException("Nested offscreen captures are not supported");
        }

        NativeImage image = null;
        try {
            GuiGraphics graphics = begin(pixelWidth, pixelHeight);
            draw.accept(graphics);
            graphics.flush();

            image = new NativeImage(width, height, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return image;
        } catch (RuntimeException | Error t) {
            if (image != null) {
                image.close();
            }
            throw t;
        } finally {
            restoreRenderState();
        }
    }

    private GuiGraphics begin(int pixelWidth, int pixelHeight) {
        captureActive = true;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (target == null || width != pixelWidth || height != pixelHeight) {
                if (target != null) {
                    target.destroyBuffers();
                }
                target = new TextureTarget(pixelWidth, pixelHeight, true, Minecraft.ON_OSX);
                width = pixelWidth;
                height = pixelHeight;
            }
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            targetBound = true;

            savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            savedSorting = RenderSystem.getVertexSorting();
            Matrix4f projection = new Matrix4f().setOrtho(
                    0.0f, pixelWidth, pixelHeight, 0.0f, 1000.0f, ClientHooks.getGuiFarPlane());
            RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelViewPushed = true;
            modelView.identity();
            modelView.translation(0.0f, 0.0f, 10000.0f - ClientHooks.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();

            Lighting.setupFor3DItems();
            return new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        } catch (RuntimeException | Error t) {
            restoreRenderState();
            throw t;
        }
    }

    private void restoreRenderState() {
        if (!captureActive) {
            return;
        }
        try {
            if (modelViewPushed) {
                Matrix4fStack modelView = RenderSystem.getModelViewStack();
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            if (savedProjection != null && savedSorting != null) {
                RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
            }
            if (targetBound) {
                target.unbindWrite();
                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            }
            Lighting.setupFor3DItems();
        } finally {
            savedProjection = null;
            savedSorting = null;
            modelViewPushed = false;
            targetBound = false;
            captureActive = false;
        }
    }

    @Override
    public void close() {
        if (captureActive) {
            JeiExportMod.LOGGER.error("[jeiexport] Offscreen renderer closed during an active capture; restoring render state");
            restoreRenderState();
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
            width = -1;
            height = -1;
        }
    }
}
