package com.recipetree.jeiexport112.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.GuiIngameForge;
import org.lwjgl.opengl.GL11;

/**
 * Export-scoped bridge for the single audited {@code RenderUtils.applyScissor} call site in
 * Multiblocked 0.8.0. Outside an active exact-layout capture, all arguments are delegated to
 * {@link GL11#glScissor(int, int, int, int)} unchanged.
 */
public final class MultiblockedScissorBridge {
    private static final MultiblockedScissorScope SCOPE =
            new MultiblockedScissorScope();

    private MultiblockedScissorBridge() {
    }

    public static void beginCapture(int recipeScale, int targetLogicalHeight,
                                    int translateX, int translateY) {
        Minecraft minecraft = requireClientThread("begin capture");
        if (!(minecraft.ingameGUI instanceof GuiIngameForge)) {
            throw drift("Minecraft.ingameGUI is " +
                    (minecraft.ingameGUI == null
                            ? "null"
                            : minecraft.ingameGUI.getClass().getName()) +
                    "; expected GuiIngameForge as used by Multiblocked 0.8.0");
        }
        ScaledResolution resolution =
                ((GuiIngameForge) minecraft.ingameGUI).getResolution();
        if (resolution == null) {
            throw drift("GuiIngameForge returned a null cached ScaledResolution");
        }

        SCOPE.begin(
                Thread.currentThread(), resolution.getScaleFactor(),
                resolution.getScaledHeight(), recipeScale, targetLogicalHeight,
                translateX, translateY
        );
    }

    /**
     * Ends the current scope, clearing it before validation so a draw or validation failure cannot
     * leak correction state into subsequent Minecraft rendering.
     */
    public static int endCapture() {
        int correctedCalls = SCOPE.end(Thread.currentThread());
        requireClientThread("end capture");
        return correctedCalls;
    }

    /** Called only from the bytecode-validated Multiblocked 0.8.0 RenderUtils method. */
    public static void glScissor(int rawX, int rawY, int rawWidth, int rawHeight) {
        if (!SCOPE.isActive()) {
            // This is the normal game-rendering path and intentionally performs no validation,
            // normalization, clamping, or fallback behavior.
            GL11.glScissor(rawX, rawY, rawWidth, rawHeight);
            return;
        }
        requireClientThread("correct scissor");
        MultiblockedScissorTransform.Box mapped = SCOPE.mapActive(
                Thread.currentThread(), rawX, rawY, rawWidth, rawHeight);
        GL11.glScissor(mapped.x, mapped.y, mapped.width, mapped.height);
    }

    private static Minecraft requireClientThread(String operation) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            throw drift(operation + " found no Minecraft client instance");
        }
        if (!minecraft.isCallingFromMinecraftThread()) {
            throw drift(operation + " must execute on Minecraft's client thread; current=" +
                    Thread.currentThread().getName());
        }
        return minecraft;
    }

    private static IllegalStateException drift(String detail) {
        return new IllegalStateException(
                "MULTIBLOCKED_SCISSOR_DRIFT: " + detail +
                        "; refusing a silent framebuffer-scissor fallback."
        );
    }
}
