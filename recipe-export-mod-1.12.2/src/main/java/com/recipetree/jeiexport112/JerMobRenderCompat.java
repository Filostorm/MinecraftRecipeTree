package com.recipetree.jeiexport112;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Corrects JER 0.9's fixed-pixel mob scissor while its recipe card is zoomed. */
final class JerMobRenderCompat {
    private static final String MOB_CATEGORY_UID = "jeresources.mob";
    private static final int MOB_CLIP_X = 7;
    private static final int MOB_CLIP_Y = 43;
    private static final int MOB_CLIP_WIDTH = 59;
    private static final int MOB_CLIP_HEIGHT = 79;
    private static final ThreadLocal<RenderScope> ACTIVE_SCOPE =
            new ThreadLocal<RenderScope>();

    private static boolean registrationAttempted;
    private static boolean registered;

    private JerMobRenderCompat() {
    }

    static ScopeToken begin(String categoryUid, int left, int top, float scale) {
        if (!MOB_CATEGORY_UID.equals(categoryUid) || !Loader.isModLoaded("jeresources")) {
            return ScopeToken.INACTIVE;
        }
        ensureRegistered();
        if (!registered) return ScopeToken.INACTIVE;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            JeiExportMod.LOGGER.error(
                    "[jeiexport] JER mob scissor correction could not start: Minecraft is null");
            return ScopeToken.INACTIVE;
        }
        int factor = new ScaledResolution(minecraft).getScaleFactor();
        ACTIVE_SCOPE.set(new RenderScope(left, top, scale, factor, minecraft.displayHeight));
        return ScopeToken.ACTIVE;
    }

    private static synchronized void ensureRegistered() {
        if (registrationAttempted) return;
        registrationAttempted = true;
        try {
            Class<?> apiClass = Class.forName("jeresources.compatibility.JERAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object mobRegistry = api.getClass().getMethod("getMobRegistry").invoke(api);
            Class<?> hookType = Class.forName("jeresources.api.render.IScissorHook");
            Class<?> mobWrapper = Class.forName("jeresources.jei.mob.MobWrapper");
            Object hook = Proxy.newProxyInstance(hookType.getClassLoader(),
                    new Class<?>[]{hookType}, new ScissorInvocationHandler());
            Method register = mobRegistry.getClass().getMethod(
                    "registerScissorHook", Class.class, hookType);
            register.invoke(mobRegistry, mobWrapper, hook);
            registered = true;
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Registered zoom-aware JER mob recipe scissor correction");
        } catch (Throwable throwable) {
            FatalErrors.rethrowIfFatal(throwable);
            JeiExportMod.LOGGER.error(
                    "[jeiexport] JER is loaded but its mob scissor correction could not be "
                            + "registered; zoomed mob cards may clip incorrectly",
                    throwable);
        }
    }

    static int[] correctedScissor(
            int cardLeft,
            int cardTop,
            float scale,
            int guiScale,
            int displayHeight) {
        int left = Math.round((cardLeft + MOB_CLIP_X * scale) * guiScale);
        int right = Math.round((cardLeft + (MOB_CLIP_X + MOB_CLIP_WIDTH) * scale) * guiScale);
        int top = Math.round((cardTop + MOB_CLIP_Y * scale) * guiScale);
        int bottom = Math.round((cardTop + (MOB_CLIP_Y + MOB_CLIP_HEIGHT) * scale) * guiScale);
        return new int[]{left, displayHeight - bottom,
                Math.max(1, right - left), Math.max(1, bottom - top)};
    }

    private static final class ScissorInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Exception {
            if ("transformScissor".equals(method.getName())) {
                Object info = arguments[0];
                RenderScope scope = ACTIVE_SCOPE.get();
                if (scope == null) return info;
                int[] corrected = correctedScissor(scope.left, scope.top, scope.scale,
                        scope.guiScale, scope.displayHeight);
                write(info, "x", corrected[0]);
                write(info, "y", corrected[1]);
                write(info, "width", corrected[2]);
                write(info, "height", corrected[3]);
                return info;
            }
            if ("toString".equals(method.getName())) return "RecipeTreeJERScissorHook";
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == arguments[0];
            return null;
        }

        private static void write(Object target, String fieldName, int value) throws Exception {
            Field field = target.getClass().getField(fieldName);
            field.setInt(target, value);
        }
    }

    private static final class RenderScope {
        final int left;
        final int top;
        final float scale;
        final int guiScale;
        final int displayHeight;

        RenderScope(int left, int top, float scale, int guiScale, int displayHeight) {
            this.left = left;
            this.top = top;
            this.scale = scale;
            this.guiScale = guiScale;
            this.displayHeight = displayHeight;
        }
    }

    static final class ScopeToken implements AutoCloseable {
        static final ScopeToken ACTIVE = new ScopeToken(true);
        static final ScopeToken INACTIVE = new ScopeToken(false);
        private final boolean active;

        private ScopeToken(boolean active) {
            this.active = active;
        }

        @Override
        public void close() {
            if (active) ACTIVE_SCOPE.remove();
        }
    }
}
