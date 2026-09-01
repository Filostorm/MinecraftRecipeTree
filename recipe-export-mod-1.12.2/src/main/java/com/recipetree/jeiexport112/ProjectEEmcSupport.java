package com.recipetree.jeiexport112;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.lang.reflect.Method;

/** Optional, shared access to ProjectE's stable 1.12 EMC API. */
final class ProjectEEmcSupport {
    static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final Object proxy;
    private final Method hasValue;
    private final Method getValue;

    private ProjectEEmcSupport(Object proxy, Method hasValue, Method getValue) {
        this.proxy = proxy;
        this.hasValue = hasValue;
        this.getValue = getValue;
    }

    static boolean isAvailable() {
        try {
            return Loader.isModLoaded("projecte");
        } catch (RuntimeException exception) {
            // Plain JVM tests deliberately do not bootstrap Forge's named-mod map.
            JeiExportMod.LOGGER.debug(
                    "[jeiexport] Forge's mod registry is not initialized yet; live ProjectE EMC "
                            + "support will remain disabled for this Recipe Tree bridge",
                    exception);
            return false;
        }
    }

    static ProjectEEmcSupport load() throws IOException {
        try {
            Class<?> api = Class.forName("moze_intel.projecte.api.ProjectEAPI");
            Method getProxy = api.getMethod("getEMCProxy");
            Object proxy = getProxy.invoke(null);
            if (proxy == null) {
                throw new ReflectiveOperationException("ProjectEAPI.getEMCProxy() returned null");
            }
            Class<?> proxyApi = getProxy.getReturnType();
            return new ProjectEEmcSupport(proxy,
                    proxyApi.getMethod("hasValue", ItemStack.class),
                    proxyApi.getMethod("getValue", ItemStack.class));
        } catch (ReflectiveOperationException exception) {
            throw new IOException("ProjectE is loaded but its EMC API could not be opened", exception);
        }
    }

    boolean hasValue(ItemStack stack) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(hasValue.invoke(proxy, normalized(stack)));
    }

    long value(ItemStack stack) throws ReflectiveOperationException {
        Object value = getValue.invoke(proxy, normalized(stack));
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    static boolean isUsableValue(boolean hasValue, long value) {
        return hasValue && value > 0 && value <= MAX_SAFE_INTEGER;
    }

    private static ItemStack normalized(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
