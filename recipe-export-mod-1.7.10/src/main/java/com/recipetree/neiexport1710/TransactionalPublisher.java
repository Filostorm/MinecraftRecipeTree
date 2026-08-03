package com.recipetree.neiexport1710;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

final class TransactionalPublisher {
    private TransactionalPublisher() {
    }

    static void publish(Path staging, Path destination) throws IOException {
        Path parent = destination.getParent();
        Path backup = parent.resolve("." + destination.getFileName() + ".previous-" + UUID.randomUUID());
        boolean hadDestination = Files.exists(destination);
        if (hadDestination) {
            move(staging.getFileSystem(), destination, backup, "existing export to backup");
        }
        try {
            move(staging.getFileSystem(), staging, destination, "staging export into place");
        } catch (IOException publicationFailure) {
            if (hadDestination && Files.exists(backup)) {
                try {
                    move(staging.getFileSystem(), backup, destination, "backup rollback");
                } catch (IOException rollbackFailure) {
                    publicationFailure.addSuppressed(rollbackFailure);
                }
            }
            throw publicationFailure;
        }
        if (hadDestination && Files.exists(backup)) {
            try {
                deleteTree(backup);
            } catch (IOException cleanupFailure) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] Publication succeeded but backup cleanup failed: {}",
                        backup, cleanupFailure);
            }
        }
    }

    private static void move(java.nio.file.FileSystem ignored, Path source, Path destination,
                             String operation) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            GtnhNeiExportMod.LOGGER.warn(
                    "[gtnh-nei-export] Atomic move unavailable while {}; using logged same-filesystem move: {}",
                    operation, unsupported.toString());
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) {
                    throw error;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
