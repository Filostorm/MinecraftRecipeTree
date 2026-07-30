package com.recipetree.reiexport118.mixin;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guards the early Mixin-selection dependency graph against Minecraft class initialization. */
final class EarlyMixinSelectionPurityTest {
    private static final String ROOT = "com.recipetree.reiexport118.";
    private static final List<String> EARLY_TYPES = List.of(
            ROOT + "mixin.ReiExportMixinConfigPlugin",
            ROOT + "mixin.ExactRuntimeSelection",
            ROOT + "compat.Mm2ExportRequestScope",
            ROOT + "compat.Mm2DeterminismContract",
            ROOT + "compat.LowDragFboViewportContract",
            ROOT + "compat.KubeJsTooltipConcurrencyContract",
            ROOT + "compat.Mm2BlockAtlasCanonicalizationContract",
            ROOT + "compat.Mm2LightmapReadinessContract");

    @Test
    void pluginAndEveryEarlyContractInitializeWithMinecraftClassesRejected() throws Exception {
        URL mainClasses = ReiExportMixinConfigPlugin.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();
        try (MinecraftRejectingChildLoader loader = new MinecraftRejectingChildLoader(
                mainClasses,
                getClass().getClassLoader())) {
            for (String type : EARLY_TYPES) {
                assertDoesNotThrow(() -> Class.forName(type, true, loader), type);
            }
            assertThrows(
                    ClassNotFoundException.class,
                    () -> loader.loadClass("net.minecraft.resources.ResourceLocation"));
        }
    }

    private static final class MinecraftRejectingChildLoader extends URLClassLoader {
        private MinecraftRejectingChildLoader(URL mainClasses, ClassLoader parent) {
            super(new URL[]{mainClasses}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.")
                    || name.startsWith("com.mojang.blaze3d.")) {
                throw new ClassNotFoundException(
                        "Minecraft classes are forbidden during early Mixin selection: " + name);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null && isEarlyType(name)) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException missingChild) {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (loaded == null) {
                    loaded = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private static boolean isEarlyType(String name) {
            return EARLY_TYPES.stream().anyMatch(
                    root -> name.equals(root) || name.startsWith(root + "$"));
        }
    }
}
