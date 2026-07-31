package com.recipetree.reiexport118.mixin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExactRuntimeSelectionTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void useBeforePublicationIsTerminalAndSameSelectionIsIdempotent() {
        ExactRuntimeSelection selection = new ExactRuntimeSelection("test selection");
        Path gameDirectory = temporaryDirectory.resolve("instance");

        assertThrows(IllegalStateException.class, () -> selection.require(gameDirectory));
        assertTrue(selection.publish(gameDirectory));
        assertFalse(selection.publish(gameDirectory.resolve(".").normalize()));
        selection.require(gameDirectory);
    }

    @Test
    void concurrentConflictingPublicationsAdmitExactlyOneDirectory() throws Exception {
        ExactRuntimeSelection selection = new ExactRuntimeSelection("test selection");
        Path firstDirectory = temporaryDirectory.resolve("first");
        Path secondDirectory = temporaryDirectory.resolve("second");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> publishAfter(
                    selection, firstDirectory, start));
            Future<Boolean> second = executor.submit(() -> publishAfter(
                    selection, secondDirectory, start));
            start.countDown();

            boolean firstWon = first.get();
            boolean secondWon = second.get();
            assertTrue(firstWon ^ secondWon, "exactly one conflicting publication must win");
            Path selected = firstWon ? firstDirectory : secondDirectory;
            Path rejected = firstWon ? secondDirectory : firstDirectory;
            selection.require(selected);
            assertThrows(IllegalStateException.class, () -> selection.require(rejected));
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean publishAfter(
            ExactRuntimeSelection selection,
            Path gameDirectory,
            CountDownLatch start
    ) throws InterruptedException {
        start.await();
        try {
            return selection.publish(gameDirectory);
        } catch (IllegalStateException expectedConflict) {
            return false;
        }
    }
}
