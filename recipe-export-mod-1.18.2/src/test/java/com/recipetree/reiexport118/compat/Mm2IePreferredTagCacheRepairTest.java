package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Mm2IePreferredTagCacheRepairTest {
    @Test
    void rejectsEveryDriftedOrInvalidInvocation() {
        HashMap<Integer, Integer> cache = new HashMap<>();
        assertThrows(
                IllegalStateException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(null, 1, key -> key));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(
                        new DerivedHashMap<>(), 1, key -> key));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(cache, null, key -> key));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(
                        cache, 1, (Function<Integer, Integer>) null));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(cache, 1, key -> null));
        assertEquals(0, cache.size(), "failed mappings must not populate the cache");
    }

    @Test
    void preservesHashMapComputeIfAbsentHitAndFailureSemantics() {
        HashMap<Integer, Integer> cache = new HashMap<>();
        cache.put(7, 119);
        assertEquals(119, Mm2IePreferredTagCacheRepair.compute(
                cache, 7, key -> {
                    throw new AssertionError("warm hit must not invoke the mapper");
                }));

        IllegalArgumentException failure = new IllegalArgumentException("mapping failed");
        IllegalArgumentException observed = assertThrows(
                IllegalArgumentException.class,
                () -> Mm2IePreferredTagCacheRepair.compute(cache, 8, key -> {
                    throw failure;
                }));
        assertEquals(failure, observed);
        assertEquals(1, cache.size(), "throwing mappings must not populate the cache");
    }

    @Test
    void serializesOnlyExactCacheMissMutationAcrossOptimizedWorkers() throws Exception {
        final int keys = 64;
        final int workers = 16;
        final int iterationsPerWorker = 4_096;
        HashMap<Integer, Integer> cache = new HashMap<>();
        AtomicIntegerArray mappingCalls = new AtomicIntegerArray(keys);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                final int workerIndex = worker;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < iterationsPerWorker; iteration++) {
                        int key = Math.floorMod(workerIndex * 31 + iteration, keys);
                        int value = Mm2IePreferredTagCacheRepair.compute(
                                cache,
                                key,
                                missing -> {
                                    mappingCalls.incrementAndGet(missing);
                                    return missing * 17;
                                });
                        assertEquals(key * 17, value);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(keys, cache.size());
        for (int key = 0; key < keys; key++) {
            assertEquals(1, mappingCalls.get(key), "mapping calls for key " + key);
        }
    }

    private static final class DerivedHashMap<K, V> extends HashMap<K, V> {
    }
}
