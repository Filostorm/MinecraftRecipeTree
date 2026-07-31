package com.recipetree.reiexport118.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** Serializes only Immersive Engineering's pinned unsafe preferred-tag cache mutation. */
public final class Mm2IePreferredTagCacheRepair {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean INSTALLATION_LOGGED = new AtomicBoolean();

    private Mm2IePreferredTagCacheRepair() {
    }

    public static <K, V> V compute(
            HashMap<K, V> cache,
            K key,
            Function<? super K, ? extends V> mappingFunction
    ) {
        if (cache == null) {
            throw failure("IEApi.oreOutputPreference is null");
        }
        if (cache.getClass() != HashMap.class) {
            throw failure("IEApi.oreOutputPreference implementation drift: expected="
                    + HashMap.class.getName() + ", actual=" + cache.getClass().getName());
        }
        if (key == null) {
            throw failure("IEApi.getPreferredTagStack supplied a null tag key");
        }
        if (mappingFunction == null) {
            throw failure("IEApi.getPreferredTagStack supplied a null mapping function");
        }

        final V value;
        synchronized (cache) {
            value = cache.computeIfAbsent(key, mappingFunction);
        }
        if (value == null) {
            throw failure("IEApi preferred-tag cache mapping returned null for key=" + key);
        }
        if (INSTALLATION_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn(
                    "[reiexport] Activated exact MM2 Immersive Engineering preferred-tag "
                            + "cache repair: synchronized only HashMap.computeIfAbsent at "
                            + "IEApi.getPreferredTagStack; unrelated optimized recipe "
                            + "conversion remains parallel");
        }
        return value;
    }

    private static IllegalStateException failure(String message) {
        LOGGER.error(
                "[reiexport] MM2 Immersive Engineering preferred-tag cache repair failed: {}",
                message);
        return new IllegalStateException(message);
    }
}
