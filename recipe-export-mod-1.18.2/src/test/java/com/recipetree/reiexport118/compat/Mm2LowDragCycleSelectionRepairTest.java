package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Mm2LowDragCycleSelectionRepairTest {
    @Test
    void fixedEpochSelectsFirstCandidateThroughTheNativeLowDragFormula() {
        long epochMillis = Mm2LowDragCycleSelectionRepair.firstCandidateEpochMillis();
        assertEquals(0L, epochMillis);
        for (int candidateCount = 1; candidateCount <= 4_096; candidateCount++) {
            int nativeIndex = Math.abs((int) (epochMillis / 1_000L) % candidateCount);
            assertEquals(0, nativeIndex, "candidate count " + candidateCount);
        }
    }

    @Test
    void fixedEpochIsStableAcrossConcurrentLayoutReads() throws Exception {
        int workers = 16;
        int readsPerWorker = 4_096;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    long observed = Long.MIN_VALUE;
                    for (int read = 0; read < readsPerWorker; read++) {
                        observed = Mm2LowDragCycleSelectionRepair.firstCandidateEpochMillis();
                        if (observed != 0L) {
                            return observed;
                        }
                    }
                    return observed;
                }));
            }
            start.countDown();
            for (Future<Long> future : futures) {
                assertEquals(0L, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
