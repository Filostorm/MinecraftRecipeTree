package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Replaces REI JEI compatibility's unsafe ingredient-type cache for exact MM2 exports. */
public final class Mm2JeiIngredientTypeCacheRepair {
    private static final Logger LOGGER = LogUtils.getLogger();

    private Mm2JeiIngredientTypeCacheRepair() {
    }

    public static <K, V> Map<K, V> install(Map<K, V> original) {
        if (original == null) {
            throw failure("Pinned JEIPluginDetector.TYPE_MAP is null at <clinit> RETURN");
        }
        if (original.getClass() != HashMap.class) {
            throw failure(
                    "Pinned JEIPluginDetector.TYPE_MAP implementation drift: expected="
                            + HashMap.class.getName() + ", actual="
                            + original.getClass().getName());
        }
        if (!original.isEmpty()) {
            throw failure(
                    "Pinned JEIPluginDetector.TYPE_MAP was populated before its <clinit> "
                            + "RETURN repair: size=" + original.size());
        }

        Map<K, V> replacement = new ConcurrentHashMap<>(original);
        if (!(replacement instanceof ConcurrentHashMap<?, ?>)) {
            throw failure(
                    "JEI ingredient-type cache replacement is not concurrent: "
                            + replacement.getClass().getName());
        }
        LOGGER.warn(
                "[reiexport] Installed exact MM2 REI JEI ingredient-type cache repair: "
                        + "java.util.HashMap -> java.util.concurrent.ConcurrentHashMap; "
                        + "parallel recipe conversion remains enabled and optimized failures "
                        + "remain terminal");
        return replacement;
    }

    private static IllegalStateException failure(String message) {
        LOGGER.error("[reiexport] MM2 REI JEI ingredient-type cache repair failed: {}", message);
        return new IllegalStateException(message);
    }
}
