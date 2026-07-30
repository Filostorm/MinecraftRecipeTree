package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import me.shedaniel.rei.api.common.registry.ReloadStage;

import java.util.List;

/** Runtime bridge between the exact MM2 REI lifecycle and the pinned JEI wrapper mixin. */
public final class Mm2JeiDeferredTaskGate {
    private static final Mm2JeiDeferredTaskSequence SEQUENCE =
            new Mm2JeiDeferredTaskSequence();

    private Mm2JeiDeferredTaskGate() {
    }

    static void beginStage(ReloadStage stage) {
        if (stage == ReloadStage.START) {
            SEQUENCE.beginStart(Thread.currentThread());
            return;
        }
        if (stage == ReloadStage.END) {
            SEQUENCE.beginEnd(Thread.currentThread());
            return;
        }
        throw new IllegalStateException("Unsupported REI reload stage for JEI queues: " + stage);
    }

    static void finishStage(ReloadStage stage) {
        if (stage == ReloadStage.START) {
            int wrappers = SEQUENCE.finishStart(Thread.currentThread());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Verified preliminary JEI START queues were already empty "
                            + "without mutation wrappers={}",
                    wrappers);
            return;
        }
        if (stage == ReloadStage.END) {
            Mm2JeiDeferredTaskSequence.Summary summary =
                    SEQUENCE.finishEnd(Thread.currentThread());
            ReiExportMod.LOGGER.info(
                    "[reiexport] Executed one authoritative JEI deferred-task generation "
                            + "during owned END wrappers={} postTasks={} "
                            + "registerCategoriesCallbacks={} registerDisplaysCallbacks={} "
                            + "registerTransferHandlersCallbacks={} "
                            + "providerOrderSha256={} remainingQueues=0",
                    summary.wrapperCount(), summary.authoritativePostTaskCount(),
                    summary.registerCategoriesCallbacks(),
                    summary.registerDisplaysCallbacks(),
                    summary.registerTransferHandlersCallbacks(),
                    summary.providerOrderSha256());
            return;
        }
        throw new IllegalStateException("Unsupported REI reload stage for JEI queues: " + stage);
    }

    public static void observeRegisterCategories(Object wrapper, String providerName) {
        SEQUENCE.observeProducer(
                wrapper, providerName, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES,
                Thread.currentThread());
    }

    public static void observeRegisterDisplays(Object wrapper, String providerName) {
        SEQUENCE.observeProducer(
                wrapper, providerName, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS,
                Thread.currentThread());
    }

    public static void observeRegisterTransferHandlers(Object wrapper, String providerName) {
        SEQUENCE.observeProducer(
                wrapper, providerName,
                Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS,
                Thread.currentThread());
    }

    /** Called at pinned wrapper {@code postStage(END)} HEAD. */
    public static void beginAuthoritativeWrapper(
            Object wrapper,
            String providerName,
            List<?> entryRegistry,
            List<?> post
    ) {
        SEQUENCE.beginAuthoritative(
                wrapper, providerName, entryRegistry, post, Thread.currentThread());
    }

    /** Called at pinned wrapper {@code postStage} RETURN for either owned stage. */
    public static void finishWrapper(
            Object wrapper,
            String providerName,
            List<?> entryRegistry,
            List<?> post,
            ReloadStage stage
    ) {
        if (stage == ReloadStage.START) {
            SEQUENCE.recordPreliminaryWrapper(
                    wrapper, providerName, entryRegistry, post, Thread.currentThread());
            return;
        }
        if (stage == ReloadStage.END) {
            SEQUENCE.finishAuthoritative(
                    wrapper, providerName, entryRegistry, post, Thread.currentThread());
            return;
        }
        throw new IllegalStateException("Unsupported REI reload stage for JEI wrapper: " + stage);
    }

    static void requireComplete() {
        SEQUENCE.requireComplete();
    }

    static void fail(Throwable failure) {
        SEQUENCE.fail(failure);
    }
}
