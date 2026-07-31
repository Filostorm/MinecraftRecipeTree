package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Mm2JeiIngredientTypeCacheRepairTest {
    @Test
    void rejectsEveryNonExactInitialCacheState() {
        assertThrows(
                IllegalStateException.class,
                () -> Mm2JeiIngredientTypeCacheRepair.install(null));
        assertThrows(
                IllegalStateException.class,
                () -> Mm2JeiIngredientTypeCacheRepair.install(new ConcurrentHashMap<>()));

        Map<String, Integer> populated = new HashMap<>();
        populated.put("already-used", 1);
        assertThrows(
                IllegalStateException.class,
                () -> Mm2JeiIngredientTypeCacheRepair.install(populated));
    }

    @Test
    void replacementCoordinatesParallelMissesWithoutSerializingWarmReads() throws Exception {
        final int keys = 64;
        final int workers = 16;
        final int iterationsPerWorker = 4_096;
        Map<Integer, Integer> cache =
                Mm2JeiIngredientTypeCacheRepair.install(new HashMap<>());
        assertInstanceOf(ConcurrentHashMap.class, cache);

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
                        int value = cache.computeIfAbsent(key, missing -> {
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
}
