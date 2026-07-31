package com.recipetree.jeiexport112;

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
import java.util.function.Consumer;

/** Bounded PNG executor. Queue saturation deliberately blocks the client producer. */
final class PngWriter {
    private static final int SATURATION_LOG_INTERVAL = 1000;

    private final ThreadPoolExecutor executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger saturationCount = new AtomicInteger();
    private final AtomicInteger saturationWarnings = new AtomicInteger();
    private final AtomicInteger peakQueued = new AtomicInteger();
    private final AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
    private final Consumer<String> failureSink;

    PngWriter(int threads, int queueCapacity, Consumer<String> failureSink) {
        this.failureSink = failureSink;
        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger number = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, "jeiexport-png-" + number.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    void submit(final BufferedImage image, final Path file) throws IOException {
        throwIfWorkerFailed();
        if (executor.isShutdown()) {
            throw new IOException("PNG executor is already shut down");
        }
        pending.incrementAndGet();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Files.createDirectories(file.getParent());
                    if (!ImageIO.write(image, "png", file.toFile())) {
                        throw new IOException("No ImageIO PNG encoder is available");
                    }
                } catch (Throwable throwable) {
                    workerFailure.compareAndSet(null, throwable);
                    try {
                        failureSink.accept("PNG write " + file + ": " + throwable);
                    } finally {
                        FatalErrors.rethrowIfFatal(throwable);
                    }
                } finally {
                    pending.decrementAndGet();
                }
            }
        };

        try {
            executor.execute(task);
            updatePeakQueueDepth();
        } catch (RejectedExecutionException rejected) {
            if (executor.isShutdown()) {
                pending.decrementAndGet();
                throw new IOException("PNG executor rejected work after shutdown", rejected);
            }
            int count = saturationCount.incrementAndGet();
            logSaturation(count);
            try {
                executor.getQueue().put(task);
                updatePeakQueueDepth();
            } catch (InterruptedException interrupted) {
                pending.decrementAndGet();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while applying PNG queue backpressure", interrupted);
            }
        }
    }

    int getPending() {
        return pending.get();
    }

    private void updatePeakQueueDepth() {
        int queued = executor.getQueue().size();
        int observed = peakQueued.get();
        while (queued > observed && !peakQueued.compareAndSet(observed, queued)) {
            observed = peakQueued.get();
        }
    }

    void finish() throws IOException {
        executor.shutdown();
        try {
            while (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                JeiExportMod.LOGGER.info("[jeiexport] Waiting for {} queued PNG encodes", pending.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new IOException("Interrupted while waiting for PNG encoder completion", e);
        }
        JeiExportMod.LOGGER.info(
                "[jeiexport] PNG executor complete; peak queued={}, saturation events={}, " +
                        "saturation warnings emitted={}",
                peakQueued.get(), saturationCount.get(), saturationWarnings.get());
        throwIfWorkerFailed();
    }

    private void logSaturation(int count) {
        if (!isSaturationLogCheckpoint(count)) {
            return;
        }
        saturationWarnings.incrementAndGet();
        if (count == 1) {
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] PNG queue saturated (event 1, pending {}). Applying producer backpressure; " +
                            "the client tick will block until an encoder slot opens. Further saturation " +
                            "events are aggregated and reported every {} events.",
                    pending.get(), SATURATION_LOG_INTERVAL);
            return;
        }
        JeiExportMod.LOGGER.warn(
                "[jeiexport] PNG queue remains saturated ({} events cumulative, pending {}). " +
                        "Bounded producer backpressure remains active.",
                count, pending.get());
    }

    static boolean isSaturationLogCheckpoint(int count) {
        return count == 1 || (count > 0 && count % SATURATION_LOG_INTERVAL == 0);
    }

    private void throwIfWorkerFailed() throws IOException {
        Throwable failure = workerFailure.get();
        if (failure == null) {
            return;
        }
        FatalErrors.rethrowIfFatal(failure);
        throw new IOException("At least one PNG encoder task failed; refusing to publish incomplete image data",
                failure);
    }
}
