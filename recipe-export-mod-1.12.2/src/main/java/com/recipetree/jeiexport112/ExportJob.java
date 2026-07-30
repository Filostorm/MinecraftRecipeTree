package com.recipetree.jeiexport112;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.ingredients.IIngredientRegistry;

import com.recipetree.jeiexport112.compat.TaaccAspectSubtypeGuard;
import com.recipetree.jeiexport112.compat.TinkersComplementFluidBlacklistGuard;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

final class ExportJob {
    private final ExportRequest request;
    private final IJeiRuntime runtime;
    private final IIngredientRegistry ingredientRegistry;
    private final Path finalOutput;
    private final Path stagingOutput;
    private final long startedNanos = System.nanoTime();
    private final ExportContext context;
    private ExportPhase phase;
    private int phaseNumber;
    private boolean complete;
    private boolean failed;

    ExportJob(ExportRequest request, IJeiRuntime runtime, IIngredientRegistry ingredientRegistry)
            throws IOException {
        this.request = request;
        this.runtime = runtime;
        this.ingredientRegistry = ingredientRegistry;
        this.finalOutput = request.output;
        Path parent = finalOutput.getParent();
        if (parent == null || finalOutput.getFileName() == null) {
            throw new IOException("Output must have a parent and file name: " + finalOutput);
        }
        Files.createDirectories(parent);
        this.stagingOutput = parent.resolve("." + finalOutput.getFileName() + ".staging-" + UUID.randomUUID());
        this.context = new ExportContext(stagingOutput, request);
        if (request.qualitySample == null) {
            this.phase = new ItemPhase(context, ingredientRegistry);
            this.phaseNumber = 1;
        } else {
            this.phase = new RecipePhase(context, runtime.getRecipeRegistry(), ingredientRegistry);
            this.phaseNumber = 2;
            JeiExportMod.LOGGER.info(
                    "[jeiexport] Quality sample mode: skipped the full item phase and selected {} recipes",
                    request.qualitySample.recipeCount());
        }
        JeiExportMod.LOGGER.info("[jeiexport] Started transactional export: staging={} final={}",
                stagingOutput, finalOutput);
    }

    void tick(long deadlineNanos) throws IOException {
        boolean first = true;
        while (!complete && (first || System.nanoTime() < deadlineNanos)) {
            first = false;
            if (!phase.step()) {
                continue;
            }
            phase.close();
            if (phaseNumber == 1) {
                phase = new RecipePhase(context, runtime.getRecipeRegistry(), ingredientRegistry);
                phaseNumber = 2;
            } else {
                phase = null;
                finishSuccess();
            }
        }
    }

    private void finishSuccess() throws IOException {
        long durationMillis = elapsedMillis();
        context.finishWritersAndImages();
        TaaccAspectSubtypeGuard.assertReadyForPublication();
        TinkersComplementFluidBlacklistGuard.assertReadyForPublication();
        QualitySampleGate.requireNoFailureEvents(
                request.qualitySample != null, context.failureCount());
        context.writeFinalMetadata(false, durationMillis);
        publishTransactional(stagingOutput, finalOutput);
        complete = true;
        JeiExportMod.LOGGER.info(
                "[jeiexport] Export complete in {} ms: items={}, recipes={}, categories={}, failures={} -> {}",
                durationMillis, context.itemCount(), context.recipeCount, context.categories.size(),
                context.failureCount(), finalOutput);
    }

    void abort(Throwable cause) {
        FatalErrors.rethrowIfFatal(cause);
        if (complete) {
            return;
        }
        failed = true;
        context.failure("fatal export failure: " + cause);
        if (phase != null) {
            try {
                phase.close();
            } catch (Throwable closeFailure) {
                FatalErrors.rethrowIfFatal(closeFailure);
                context.failure("closing failed phase: " + closeFailure);
            }
        }
        try {
            context.finishWritersAndImages();
        } catch (Throwable closeFailure) {
            FatalErrors.rethrowIfFatal(closeFailure);
            context.failure("closing failed export resources: " + closeFailure);
        }
        try {
            context.writeFinalMetadata(true, elapsedMillis());
        } catch (Throwable metadataFailure) {
            FatalErrors.rethrowIfFatal(metadataFailure);
            JeiExportMod.LOGGER.error("[jeiexport] Could not write aborted export diagnostics in {}",
                    stagingOutput, metadataFailure);
        }
        complete = true;
        JeiExportMod.LOGGER.error(
                "[jeiexport] Export aborted. Final output was not modified. Diagnostic staging remains at {}",
                stagingOutput, cause);
    }

    private long elapsedMillis() {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    boolean isComplete() {
        return complete;
    }

    boolean isFailed() {
        return failed;
    }

    String progress() {
        if (phase == null) {
            return "finalizing";
        }
        return phase.label() + " " + phase.done() + "/" + phase.total() +
                ", total items=" + context.itemCount() +
                ", recipes=" + context.recipeCount +
                ", PNG pending=" + context.pngWriter.getPending();
    }

    Path output() {
        return finalOutput;
    }

    static void publishTransactional(Path staging, Path destination) throws IOException {
        requireReplaceableDestination(destination);
        Path parent = destination.getParent();
        Path backup = parent.resolve("." + destination.getFileName() + ".previous-" + UUID.randomUUID());
        boolean hadDestination = Files.exists(destination);
        if (hadDestination) {
            moveWithLoggedAtomicFallback(destination, backup, "existing export to backup");
        }
        try {
            moveWithLoggedAtomicFallback(staging, destination, "staging export into place");
        } catch (IOException publishFailure) {
            if (hadDestination && Files.exists(backup)) {
                try {
                    moveWithLoggedAtomicFallback(backup, destination, "rollback backup into place");
                } catch (IOException rollbackFailure) {
                    publishFailure.addSuppressed(rollbackFailure);
                    JeiExportMod.LOGGER.error("[jeiexport] Export publish rollback also failed: {}",
                            rollbackFailure.toString());
                }
            }
            throw publishFailure;
        }

        if (hadDestination && Files.exists(backup)) {
            try {
                deleteTree(backup);
            } catch (IOException cleanupFailure) {
                JeiExportMod.LOGGER.warn("[jeiexport] Published successfully but could not remove backup {}: {}",
                        backup, cleanupFailure.toString());
            }
        }
    }

    private static void requireReplaceableDestination(Path destination) throws IOException {
        if (Files.isSymbolicLink(destination)) {
            throw new IOException(
                    "Export destination became a symbolic link before publication: " + destination
            );
        }
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "Export destination became a non-directory before publication: " + destination
            );
        }
    }

    private static void moveWithLoggedAtomicFallback(Path source, Path destination, String operation)
            throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Atomic directory move is unavailable while {}; using a same-filesystem " +
                            "non-atomic move: {}", operation, unsupported.toString());
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
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
