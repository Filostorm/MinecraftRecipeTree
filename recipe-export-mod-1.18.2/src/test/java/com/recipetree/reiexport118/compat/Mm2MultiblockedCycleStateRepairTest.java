package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2MultiblockedCycleStateRepairTest {
    @Test
    void returnsTheFirstCandidateByIdentityWithoutMutatingTheArray() {
        Object first = new Object();
        Object[] candidates = {first, new Object(), new Object()};
        Object[] before = Arrays.copyOf(candidates, candidates.length);

        for (int read = 0; read < 16_384; read++) {
            assertSame(first, Mm2MultiblockedCycleStateRepair.firstCandidate(candidates));
        }

        assertArrayEquals(before, candidates);
    }

    @Test
    void invalidAuditedCandidateArraysFailClosed() {
        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> Mm2MultiblockedCycleStateRepair.firstCandidate((Object[]) null));
        assertTrue(missing.getMessage().contains("candidate array is null"));

        IllegalStateException empty = assertThrows(
                IllegalStateException.class,
                () -> Mm2MultiblockedCycleStateRepair.firstCandidate(new Object[0]));
        assertTrue(empty.getMessage().contains("candidate array is empty"));

        IllegalStateException nullFirst = assertThrows(
                IllegalStateException.class,
                () -> Mm2MultiblockedCycleStateRepair.firstCandidate(
                        new Object[]{null, new Object()}));
        assertTrue(nullFirst.getMessage().contains("candidate index 0 is null"));
    }

    @Test
    void interceptionLedgerFailsOnZeroAndReportsRecordedHits() {
        Mm2MultiblockedCycleStateRepair.InterceptionCounter counter =
                new Mm2MultiblockedCycleStateRepair.InterceptionCounter();

        IllegalStateException zero = assertThrows(
                IllegalStateException.class,
                () -> counter.requireObserved("at test boundary"));
        assertTrue(zero.getMessage().contains("zero interceptions at test boundary"));
        assertThrows(IllegalArgumentException.class, () -> counter.requireObserved(" "));

        assertEquals(1L, counter.record());
        assertEquals(2L, counter.record());
        assertEquals(2L, counter.requireObserved("at test boundary"));
    }

    @Test
    void concurrentReadsAlwaysReturnTheSameCandidateReference() throws Exception {
        int workers = 16;
        int readsPerWorker = 4_096;
        Object first = new Object();
        Object[] candidates = {first, new Object(), new Object(), new Object(), new Object()};
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    Object observed = null;
                    for (int read = 0; read < readsPerWorker; read++) {
                        observed = Mm2MultiblockedCycleStateRepair.firstCandidate(candidates);
                        if (observed != first) {
                            return observed;
                        }
                    }
                    return observed;
                }));
            }
            start.countDown();
            for (Future<Object> future : futures) {
                assertSame(first, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
