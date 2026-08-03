package com.recipetree.neiexport1710;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded encoder: saturation applies producer backpressure instead of growing heap usage. */
final class PngWriter {
    private static final int SATURATION_LOG_INTERVAL = 1000;

    private final ThreadPoolExecutor executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger saturationEvents = new AtomicInteger();
    private final AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();

    PngWriter(int threads, int queueCapacity) {
        executor = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger index = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable,
                                "gtnh-nei-export-png-" + index.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    void submit(final BufferedImage image, final Path file) throws IOException {
        throwIfFailed();
        if (executor.isShutdown()) {
            throw new IOException("PNG writer is shut down");
        }
        pending.incrementAndGet();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Files.createDirectories(file.getParent());
                    if (!ImageIO.write(image, "png", file.toFile())) {
                        throw new IOException("No PNG ImageIO encoder is installed");
                    }
                } catch (Throwable error) {
                    workerFailure.compareAndSet(null, error);
                    GtnhNeiExportMod.LOGGER.error("[gtnh-nei-export] PNG_WRITE: {}", file, error);
                    FatalErrors.rethrowIfFatal(error);
                } finally {
                    pending.decrementAndGet();
                }
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException saturated) {
            if (executor.isShutdown()) {
                pending.decrementAndGet();
                throw new IOException("PNG writer rejected work after shutdown", saturated);
            }
            int count = saturationEvents.incrementAndGet();
            if (isSaturationLogCheckpoint(count)) {
                GtnhNeiExportMod.LOGGER.warn(
                        "[gtnh-nei-export] PNG queue saturation event {}; applying bounded producer backpressure",
                        count);
            }
            try {
                executor.getQueue().put(task);
            } catch (InterruptedException interrupted) {
                pending.decrementAndGet();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for PNG queue capacity", interrupted);
            }
        }
    }

    int pending() {
        return pending.get();
    }

    void finish() throws IOException {
        executor.shutdown();
        try {
            while (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                GtnhNeiExportMod.LOGGER.info(
                        "[gtnh-nei-export] Waiting for {} PNG writes", pending.get());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new IOException("Interrupted while finishing PNG writes", interrupted);
        }
        throwIfFailed();
    }

    static boolean isSaturationLogCheckpoint(int count) {
        return count == 1 || (count > 0 && count % SATURATION_LOG_INTERVAL == 0);
    }

    private void throwIfFailed() throws IOException {
        Throwable error = workerFailure.get();
        if (error != null) {
            FatalErrors.rethrowIfFatal(error);
            throw new ExportFailure("PNG_WRITE", "background PNG encoding failed", error);
        }
    }
}
