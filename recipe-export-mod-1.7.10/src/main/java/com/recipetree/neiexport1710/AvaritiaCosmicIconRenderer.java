package com.recipetree.neiexport1710;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GLContext;

/** Makes GTNH Avaritia's native inventory renderer fail closed when its cosmic shader is unavailable. */
final class AvaritiaCosmicIconRenderer {

    static final String CONTRACT = "gtnh-avaritia-1.77-cosmic-inventory-shader-v1";

    private static final String COSMIC_INTERFACE =
            "fox.spiteful.avaritia.render.ICosmicRenderItem";
    private static final String COSMIC_RENDERER =
            "fox.spiteful.avaritia.render.CosmicItemRenderer";
    private static final String COSMIC_STATE =
            "fox.spiteful.avaritia.render.CosmicRenderShenanigans";
    private static final String SHADER_HELPER =
            "fox.spiteful.avaritia.render.ShaderHelper";

    private final String itemClassName;
    private final String rendererClassName;

    private AvaritiaCosmicIconRenderer(String itemClassName, String rendererClassName) {
        this.itemClassName = itemClassName;
        this.rendererClassName = rendererClassName;
    }

    static AvaritiaCosmicIconRenderer createIfTarget(ItemStack stack) throws ExportFailure {
        if (stack == null || stack.getItem() == null
                || findNamedInterface(stack.getItem().getClass(), COSMIC_INTERFACE) == null) {
            return null;
        }
        IItemRenderer renderer = MinecraftForgeClient.getItemRenderer(
                stack, IItemRenderer.ItemRenderType.INVENTORY);
        if (renderer == null || !hasNamedSuperclass(renderer.getClass(), COSMIC_RENDERER)) {
            throw new ExportFailure(
                    "ITEM_ICON_RENDER",
                    CONTRACT + " expected the registered inventory renderer for "
                            + stack.getItem().getClass().getName()
                            + " to extend " + COSMIC_RENDERER + "; got "
                            + (renderer == null ? "<null>" : renderer.getClass().getName()));
        }
        return new AvaritiaCosmicIconRenderer(
                stack.getItem().getClass().getName(), renderer.getClass().getName());
    }

    void drawExactlyOnce(
            OffscreenRenderer.DrawCall ownerDraw, String canonicalKey) throws Exception {
        boolean previousShaderSupport = OpenGlHelper.shadersSupported;
        Throwable failure = null;
        int program = 0;
        boolean initialized = false;
        try {
            if (!previousShaderSupport) {
                ContextCapabilities capabilities = GLContext.getCapabilities();
                boolean shaderObjectsAvailable = capabilities.OpenGL20
                        || (capabilities.GL_ARB_shader_objects
                                && capabilities.GL_ARB_vertex_shader
                                && capabilities.GL_ARB_fragment_shader);
                if (!shaderObjectsAvailable) {
                    throw new IllegalStateException(
                            CONTRACT + " requires GLSL shader objects, but the active Minecraft "
                                    + "OpenGL context does not provide them");
                }
                OpenGlHelper.shadersSupported = true;
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] AVARITIA_SHADER_SUPPORT_RECOVERED item={}; the active "
                                + "OpenGL context exposes shader objects even though Minecraft's "
                                + "cached support flag was false",
                        canonicalKey);
            }

            Class<?> shaderHelper = Class.forName(SHADER_HELPER);
            Field cosmicShader = shaderHelper.getField("cosmicShader");
            program = cosmicShader.getInt(null);
            if (program <= 0) {
                Method initShaders = shaderHelper.getMethod("initShaders");
                initShaders.invoke(null);
                program = cosmicShader.getInt(null);
                initialized = true;
            }
            requireProgram(program);

            ownerDraw.draw();

            int programAfterDraw = cosmicShader.getInt(null);
            requireProgram(programAfterDraw);
            if (programAfterDraw != program) {
                throw new IllegalStateException(
                        CONTRACT + " cosmic shader program changed during one inventory draw: "
                                + program + " -> " + programAfterDraw);
            }
        } catch (Throwable error) {
            failure = error;
        } finally {
            try {
                ARBShaderObjects.glUseProgramObjectARB(0);
                Class<?> cosmicState = Class.forName(COSMIC_STATE);
                cosmicState.getField("inventoryRender").setBoolean(null, false);
                cosmicState.getField("cosmicOpacity").setFloat(null, 1.0F);
            } catch (Throwable restore) {
                failure = merge(failure, restore);
            }
            OpenGlHelper.shadersSupported = previousShaderSupport;
        }

        if (failure != null) {
            FatalErrors.rethrowIfFatal(failure);
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw new IllegalStateException(CONTRACT + " failed", failure);
        }

        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] AVARITIA_COSMIC_ICON_RENDER_VERIFIED item={} itemClass={} "
                        + "renderer={} shaderProgram={} initialized={}",
                canonicalKey, itemClassName, rendererClassName, program, initialized);
    }

    static int requireProgram(int program) {
        if (program <= 0) {
            throw new IllegalStateException(
                    CONTRACT + " could not initialize Avaritia's cosmic shader; refusing to "
                            + "export the unshaded mask texture");
        }
        return program;
    }

    private static Class<?> findNamedInterface(Class<?> type, String interfaceName) {
        if (type == null) {
            return null;
        }
        for (Class<?> candidate : type.getInterfaces()) {
            if (interfaceName.equals(candidate.getName())) {
                return candidate;
            }
            Class<?> nested = findNamedInterface(candidate, interfaceName);
            if (nested != null) {
                return nested;
            }
        }
        return findNamedInterface(type.getSuperclass(), interfaceName);
    }

    private static boolean hasNamedSuperclass(Class<?> type, String className) {
        while (type != null) {
            if (className.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
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
}
