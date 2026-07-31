package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TransactionalSnapshotTest {
    @TempDir
    Path tempDirectory;

    @Test
    void completedStagingDirectoryReplacesThePreviousSnapshot() throws Exception {
        Path destination = tempDirectory.resolve("jei-exports");
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("old.txt"), "old");

        ExportContext context = new ExportContext(
                destination, 4, new PackIdentity("Test Pack", "1.0", "explicit-request"));
        Files.writeString(context.root.resolve("new.txt"), "new");

        context.publishCompletedSnapshot();

        assertEquals("new", Files.readString(destination.resolve("new.txt")));
        assertFalse(Files.exists(destination.resolve("old.txt")));
        assertFalse(Files.exists(context.root));
    }

    @Test
    void pendingWritesPreventPromotionAndLeaveThePreviousSnapshotUntouched() throws IOException {
        Path destination = tempDirectory.resolve("jei-exports");
        Files.createDirectories(destination);
        Files.writeString(destination.resolve("old.txt"), "old");

        ExportContext context = new ExportContext(
                destination, 4, new PackIdentity("Test Pack", "1.0", "explicit-request"));
        context.pendingWrites.incrementAndGet();

        assertThrows(IOException.class, context::publishCompletedSnapshot);
        assertEquals("old", Files.readString(destination.resolve("old.txt")));
    }
}
