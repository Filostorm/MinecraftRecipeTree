package com.recipetree.reiexport118;

import com.mojang.blaze3d.platform.NativeImage;
import com.recipetree.reiexport118.mixin.NativeImagePixelsAccessor;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class PngWriter implements AutoCloseable {
    private final ExportContext context;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicInteger peakPending = new AtomicInteger();
    private final AtomicInteger saturationEvents = new AtomicInteger();

    private final class PngTask implements Runnable {
        private final NativeImage image;
        private final Path destination;
        private final AtomicBoolean owned = new AtomicBoolean(true);

        private PngTask(NativeImage image, Path destination) {
            this.image = image;
            this.destination = destination;
        }

        @Override
        public void run() {
            if (!owned.compareAndSet(true, false)) {
                return;
            }
            try (image) {
                Files.createDirectories(destination.getParent());
                writeDirect(image, destination);
            } catch (Throwable throwable) {
                context.failure("PNG write " + context.relative(destination) + ": " + throwable);
            } finally {
                pending.decrementAndGet();
            }
        }

        private void discard() {
            if (owned.compareAndSet(true, false)) {
                try {
                    image.close();
                } finally {
                    pending.decrementAndGet();
                }
            }
        }
    }

    PngWriter(ExportContext context, int threads, int queueCapacity) {
        this.context = context;
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "reiexport-png-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                (task, pool) -> {
                    if (pool.isShutdown()) {
                        throw new RejectedExecutionException("PNG writer is shut down");
                    }
                    int events = saturationEvents.incrementAndGet();
                    if (events == 1 || (events & (events - 1)) == 0) {
                        ReiExportMod.LOGGER.warn(
                                "[reiexport] PNG queue saturation event {} ({} pending); "
                                        + "applying bounded producer backpressure",
                                events,
                                pending.get()
                        );
                    }
                    task.run();
                });
        ReiExportMod.LOGGER.info(
                "[reiexport] PNG encoder initialized: direct STB filename API, threads={}, "
                        + "queueCapacity={}; per-image executable callbacks are disabled",
                threads,
                queueCapacity
        );
    }

    private static void writeDirect(NativeImage image, Path destination) throws IOException {
        if (image.format() != NativeImage.Format.RGBA) {
            throw new IOException("Direct PNG encoder requires RGBA NativeImage; received "
                    + image.format());
        }
        if (Files.exists(destination)) {
            throw new IOException("Refusing to replace an existing PNG: " + destination);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int byteCount;
        try {
            byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        } catch (ArithmeticException overflow) {
            throw new IOException("PNG dimensions overflow a direct RGBA buffer: "
                    + width + "x" + height, overflow);
        }
        long address = ((NativeImagePixelsAccessor) (Object) image).reiexport$getPixels();
        if (address == 0L) {
            throw new IOException("NativeImage allocation is closed before PNG encoding");
        }
        ByteBuffer pixels = MemoryUtil.memByteBuffer(address, byteCount);
        if (!STBImageWrite.stbi_write_png(
                destination.toString(), width, height, 4, pixels, Math.multiplyExact(width, 4))) {
            throw new IOException("Direct STB PNG encoder returned false for " + destination);
        }
        if (!Files.isRegularFile(destination) || Files.size(destination) <= 0L) {
            throw new IOException("Direct STB PNG encoder produced no regular non-empty file: "
                    + destination);
        }
    }

    int pending() {
        return pending.get();
    }

    void submit(NativeImage image, Path destination) {
        PngTask task = new PngTask(image, destination);
        int nowPending = pending.incrementAndGet();
        peakPending.accumulateAndGet(nowPending, Math::max);
        try {
            executor.execute(task);
        } catch (Throwable throwable) {
            task.discard();
            throw throwable;
        }
    }

    void awaitCompletion() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
            throw new IllegalStateException("PNG writer did not drain within 30 minutes; " + pending() + " writes remain.");
        }
        ReiExportMod.LOGGER.info(
                "[reiexport] PNG encoder complete: peakPending={}, saturationEvents={}",
                peakPending.get(),
                saturationEvents.get()
        );
    }

    @Override
    public void close() {
        for (Runnable abandoned : executor.shutdownNow()) {
            if (abandoned instanceof PngTask task) {
                task.discard();
            } else {
                context.failure("PNG writer returned an unknown queued task during shutdown: "
                        + abandoned.getClass().getName());
            }
        }
    }
}
