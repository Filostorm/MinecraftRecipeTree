package com.recipetree.reiexport118;

import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyCompatibility;
import com.recipetree.reiexport118.compat.IndustrialForegoingOreTagOrderCompatibility;
import com.recipetree.reiexport118.compat.IndustrialForegoingRecipeListOrderCompatibility;
import com.recipetree.reiexport118.compat.Mm2BlockAtlasCanonicalization;
import com.recipetree.reiexport118.compat.Mm2DeterminismCompatibility;
import com.recipetree.reiexport118.compat.Mm2MultiblockedCycleStateRepair;
import com.recipetree.reiexport118.compat.Mm2OffscreenGlintClock;
import com.recipetree.reiexport118.compat.Mm2LightmapReadiness;
import com.recipetree.reiexport118.compat.Mm2SpiritEntityRenderDeterminism;
import com.recipetree.reiexport118.compat.Mm2UnattendedUiScope;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

final class ExportJob {
    interface PhaseRunner extends AutoCloseable {
        boolean step() throws Exception;

        String label();

        int done();

        int total();

        @Override
        default void close() throws Exception {
        }
    }

    interface PhaseFactory {
        PhaseRunner create() throws IOException;
    }

    private final ExportCoordinator.Claim claim;
    private final ExportContext context;
    private final ExportPlan plan;
    private final Mm2OffscreenGlintClock.AuditSnapshot glintAuditBaseline;
    private final Mm2SpiritEntityRenderDeterminism.AuditSnapshot spiritAuditBaseline;
    private final Deque<PhaseFactory> remaining = new ArrayDeque<>();
    private final long startedAt = System.currentTimeMillis();
    private PhaseRunner runner;
    private Mm2BlockAtlasCanonicalization.Scope blockAtlasScope;
    private boolean blockAtlasScopeStarted;
    private boolean terminal;

    ExportJob(ExportCoordinator.Claim claim, ExportContext context, ExportPlan plan) {
        this.claim = claim;
        this.context = context;
        this.plan = plan;
        this.glintAuditBaseline = Mm2OffscreenGlintClock.auditSnapshot();
        this.spiritAuditBaseline = Mm2SpiritEntityRenderDeterminism.auditSnapshot();
        if (!claim.request().isQualitySample() || !claim.request().qualityItemSample.isEmpty()) {
            remaining.add(() -> new ItemPhase(context));
        }
        remaining.add(() -> new RecipePhase(context, plan));
    }

    void tick() {
        if (terminal) {
            return;
        }
        long budgetNanos = claim.request().tickBudgetMs * 1_000_000L;
        long tickStart = System.nanoTime();
        try {
            beginBlockAtlasScope();
            while (System.nanoTime() - tickStart < budgetNanos) {
                if (runner == null) {
                    PhaseFactory next = remaining.poll();
                    if (next == null) {
                        if (context.pendingPngWrites() > 0) {
                            overlay("flushing PNGs (" + context.pendingPngWrites() + " pending)");
                            return;
                        }
                        finish();
                        return;
                    }
                    runner = next.create();
                }
                if (runner.step()) {
                    runner.close();
                    log("Finished " + runner.label() + ": " + runner.done() + "/" + runner.total(), ChatFormatting.GREEN);
                    runner = null;
                }
            }
            overlay(statusLine());
        } catch (Throwable throwable) {
            fail(throwable);
        }
    }

    private String statusLine() {
        if (runner == null) {
            return "starting next phase";
        }
        return runner.label() + " " + runner.done() + "/" + runner.total();
    }

    private void finish() throws Exception {
        terminal = true;
        closeBlockAtlasScope();
        Mm2MultiblockedCycleStateRepair.requireObservedBeforePublication();
        if (Mm2DeterminismCompatibility.isLifecycleArmed()) {
            IndustrialForegoingOreTagOrderCompatibility
                    .requireObservedBeforePublication();
            IndustrialForegoingRecipeListOrderCompatibility
                    .requireObservedBeforePublication();
            Mm2OffscreenGlintClock.requireKnownSampleInterceptionSince(
                    glintAuditBaseline,
                    claim.request().output);
        }
        Mm2SpiritEntityRenderDeterminism.requireObservedSince(
                spiritAuditBaseline,
                claim.request().output);
        plan.setItemCountAtFinish(context.catalogCount());
        boolean rejectedForFailures = claim.request().failOnError && !context.failures.isEmpty();
        KubeJsTooltipConcurrencyCompatibility.requireHealthyIfApplicable();
        Mm2LightmapReadiness.requireHealthyBeforePublication();
        Mm2UnattendedUiScope.requireHealthyBeforePublication();
        Mm2UnattendedUiScope.releaseIfActive("all native captures complete");
        context.finish(rejectedForFailures, System.currentTimeMillis() - startedAt, plan);
        if (rejectedForFailures) {
            String message = "Export rendered with " + context.failures.size()
                    + " recorded failures; failOnError prevented publication. Staging diagnostics: " + context.root;
            ExportCoordinator.completeFailure(claim, message);
            log(message, ChatFormatting.RED);
        } else {
            context.publish();
            String message = String.format(Locale.ROOT,
                    "Export complete in %.1fs: items=%d recipes=%d categories=%d warnings=%d -> %s",
                    (System.currentTimeMillis() - startedAt) / 1000.0,
                    plan.itemCountAtFinish(),
                    context.recipeCount,
                    context.categoryCount,
                    context.warnings.size(),
                    context.finalRoot);
            ExportCoordinator.completeSuccess(claim, message);
            log(message, ChatFormatting.GREEN);
        }
        context.close();
    }

    void abort(String reason) {
        if (!terminal) {
            fail(new IllegalStateException(reason));
        }
    }

    private void fail(Throwable throwable) {
        terminal = true;
        ReiExportMod.LOGGER.error("[reiexport] Export failed", throwable);
        if (runner != null) {
            try {
                runner.close();
            } catch (Throwable closeFailure) {
                ReiExportMod.LOGGER.error("[reiexport] Phase close also failed", closeFailure);
            }
            runner = null;
        }
        try {
            closeBlockAtlasScope();
        } catch (Throwable closeFailure) {
            throwable.addSuppressed(closeFailure);
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 block-atlas scope close also failed",
                    closeFailure);
        }
        try {
            Mm2UnattendedUiScope.releaseIfActive("failed export cleanup");
        } catch (Throwable closeFailure) {
            throwable.addSuppressed(closeFailure);
            ReiExportMod.LOGGER.error(
                    "[reiexport] MM2 unattended UI scope close also failed",
                    closeFailure);
        }
        try {
            context.failure("Fatal exporter failure: " + throwable);
            plan.setItemCountAtFinish(context.catalogCount());
            context.finish(true, System.currentTimeMillis() - startedAt, plan);
        } catch (Throwable finishFailure) {
            ReiExportMod.LOGGER.error("[reiexport] Writing failure diagnostics also failed", finishFailure);
        } finally {
            context.close();
        }
        String message = "Export failed; no dataset was published. Staging diagnostics: " + context.root
                + "; cause=" + throwable;
        ExportCoordinator.completeFailure(claim, message);
        log(message, ChatFormatting.RED);
    }

    private void beginBlockAtlasScope() {
        if (blockAtlasScopeStarted) {
            return;
        }
        blockAtlasScope = Mm2BlockAtlasCanonicalization.beginIfApplicable();
        blockAtlasScopeStarted = true;
    }

    private void closeBlockAtlasScope() {
        if (!blockAtlasScopeStarted) {
            return;
        }
        Mm2BlockAtlasCanonicalization.Scope current = blockAtlasScope;
        blockAtlasScope = null;
        blockAtlasScopeStarted = false;
        if (current == null) {
            throw new IllegalStateException(
                    "MM2 block-atlas scope state was marked started without an owned scope");
        }
        current.close();
    }

    private static void overlay(String message) {
        Minecraft.getInstance().gui.setOverlayMessage(new TextComponent("[REI Export] " + message), false);
    }

    private static void log(String message, ChatFormatting color) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    new TextComponent("[REI Export] " + message).withStyle(color), false);
        }
        ReiExportMod.LOGGER.info("[reiexport] {}", message);
    }
}
