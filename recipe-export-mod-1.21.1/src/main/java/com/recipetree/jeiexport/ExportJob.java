package com.recipetree.jeiexport;

import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Tick-driven export state machine. All rendering happens on the render thread in
 * time-budgeted slices each client tick, so the game stays responsive even when a
 * modpack has six figures of recipes. PNG encoding happens on the IO pool.
 */
public final class ExportJob {
    public enum Phase {ITEMS, RECIPES, EMC, MOBS, BLOCK_DROPS, TRADES}

    /** Max time spent exporting per client tick (~3 frames at 60fps feels fine). */
    private static final long TICK_BUDGET_NANOS = 45_000_000L;

    @Nullable
    private static ExportJob current;

    private final ExportContext ctx;
    private final Deque<Phase> remaining;
    @Nullable
    private final IJeiRuntime runtime;
    @Nullable
    private PhaseRunner runner;
    private boolean cancelled;
    private boolean cancellationPrepared;
    private boolean contextClosed;
    private final long startedAtMillis = System.currentTimeMillis();

    public interface PhaseRunner {
        /** Performs one unit of work. @return true when the phase is finished. */
        boolean step() throws Exception;

        String label();

        int done();

        int total();

        default void close() throws IOException {
        }
    }

    private ExportJob(@Nullable IJeiRuntime runtime, EnumSet<Phase> phases, ExportContext ctx) {
        this.runtime = runtime;
        this.ctx = ctx;
        // EnumSet iterates in declaration order: ITEMS, RECIPES, MOBS.
        this.remaining = new ArrayDeque<>(phases);
    }

    public static synchronized void start(@Nullable IJeiRuntime runtime, EnumSet<Phase> phases, int iconScale) throws IOException {
        if (current != null) {
            throw new IllegalStateException("An export is already running");
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("At least one export phase is required");
        }
        if (iconScale < 1 || iconScale > 16) {
            throw new IllegalArgumentException("iconScale must be between 1 and 16");
        }
        var gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        PackIdentity packIdentity = PackIdentityResolver.resolve(gameDirectory);
        ExportContext ctx = new ExportContext(gameDirectory.resolve("jei-exports"), iconScale, packIdentity);
        current = new ExportJob(runtime, phases, ctx);
    }

    @Nullable
    public static ExportJob current() {
        return current;
    }

    public static boolean cancel() {
        ExportJob job = current;
        if (job == null) {
            return false;
        }
        job.cancelled = true;
        return true;
    }

    /** Immediate cleanup (e.g. when leaving the world). Must run on the render thread. */
    public static synchronized void abortNow() {
        ExportJob job = current;
        if (job != null) {
            try {
                job.finish(true);
            } catch (Exception e) {
                JeiExportMod.LOGGER.error("Failed to clean up export", e);
                current = null;
            }
        }
    }

    public String statusLine() {
        PhaseRunner r = this.runner;
        if (r == null) {
            return "starting...";
        }
        return String.format(Locale.ROOT, "%s %d/%d", r.label(), r.done(), r.total());
    }

    public void tick() {
        long start = System.nanoTime();
        try {
            if (cancelled) {
                if (!cancellationPrepared) {
                    remaining.clear();
                    if (runner != null) {
                        runner.close();
                        runner = null;
                    }
                    cancellationPrepared = true;
                }
                if (ctx.pendingWrites.get() > 0) {
                    overlay("cancelling; flushing images (" + ctx.pendingWrites.get() + " pending)");
                    return;
                }
                finish(true);
                return;
            }
            while (System.nanoTime() - start < TICK_BUDGET_NANOS) {
                if (ctx.pendingWrites.get() >= ExportContext.MAX_PENDING_IMAGE_WRITES) {
                    overlay("applying image-write backpressure (" + ctx.pendingWrites.get() + " pending)");
                    return;
                }
                if (runner == null) {
                    Phase next = remaining.poll();
                    if (next == null) {
                        // Let async PNG writes drain before declaring victory.
                        if (ctx.pendingWrites.get() > 0) {
                            overlay("flushing images (" + ctx.pendingWrites.get() + " pending)");
                            return;
                        }
                        finish(false);
                        return;
                    }
                    runner = createRunner(next);
                }
                if (runner.step()) {
                    runner.close();
                    chat("Finished " + runner.label() + ": " + runner.done() + "/" + runner.total(), ChatFormatting.GREEN);
                    runner = null;
                }
            }
            overlay(statusLine());
        } catch (Throwable t) {
            JeiExportMod.LOGGER.error("JEI export failed", t);
            chat("Export failed: " + t, ChatFormatting.RED);
            try {
                finish(true);
            } catch (Exception e) {
                JeiExportMod.LOGGER.error("Cleanup after failure also failed", e);
                current = null;
            }
        }
    }

    private PhaseRunner createRunner(Phase phase) throws IOException {
        return switch (phase) {
            case ITEMS -> new ItemExporter(ctx, requireRuntime());
            case RECIPES -> new RecipeExporter(ctx, requireRuntime());
            case EMC -> new ProjectEEmcExporter(ctx, requireRuntime());
            case MOBS -> new MobExporter(ctx);
            case BLOCK_DROPS -> new BlockDropsExporter(ctx);
            case TRADES -> new TradeExporter(ctx, requireRuntime());
        };
    }

    private IJeiRuntime requireRuntime() {
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime is not available");
        }
        return runtime;
    }

    private void finish(boolean aborted) throws IOException {
        if (runner != null) {
            try {
                runner.close();
            } catch (Exception e) {
                JeiExportMod.LOGGER.error("Failed to close phase runner", e);
            }
            runner = null;
        }
        int items = ctx.catalogCount();
        int recipes = ctx.recipeCount;
        int categories = ctx.categoryCount;
        int mobs = ctx.mobCount;
        int failures = ctx.failures.size();
        if (!contextClosed) {
            ctx.finishAndClose(aborted, System.currentTimeMillis() - startedAtMillis);
            contextClosed = true;
        }
        if (!aborted) {
            ctx.publishCompletedSnapshot();
        }
        current = null;
        chat(String.format(Locale.ROOT,
                        "%s in %.1fs. items=%d recipes=%d categories=%d mobs=%d failures=%d -> %s",
                        aborted ? "Export stopped" : "Export complete",
                        (System.currentTimeMillis() - startedAtMillis) / 1000.0,
                        items, recipes, categories, mobs, failures,
                        aborted ? ctx.root : ctx.finalRoot),
                aborted ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
        if (!aborted) {
            try {
                if (AutomationOptions.exitOnCompleteEnabled()) {
                    JeiExportMod.LOGGER.info("[jeiexport] Export completed; closing Minecraft as requested");
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.execute(minecraft::stop);
                }
            } catch (IllegalArgumentException e) {
                JeiExportMod.LOGGER.error("[jeiexport] Invalid automation option; Minecraft will remain open", e);
            }
        }
    }

    private static void overlay(String message) {
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.literal("[JEI Export] " + message), false);
    }

    static void chat(String message, ChatFormatting color) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[JEI Export] " + message).withStyle(color), false);
        }
        JeiExportMod.LOGGER.info("[jeiexport] {}", message);
    }
}
