package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2JeiDeferredTaskSequenceTest {
    private static final Runnable TASK = () -> {
    };
    private static final Object WRAPPER = new Object();
    private static final String PROVIDER = "MekanismJEI[mekanism:jei_plugin]";

    @Test
    void observesEmptyStartWithoutMutationThenOneAuthoritativeEndGeneration() {
        Mm2JeiDeferredTaskSequence sequence = new Mm2JeiDeferredTaskSequence();
        Thread owner = Thread.currentThread();
        Object first = new Object();
        Object second = new Object();
        ClearTrackingList<Runnable> startEntries = new ClearTrackingList<>();
        ClearTrackingList<Runnable> startPost = new ClearTrackingList<>();

        sequence.beginStart(owner);
        sequence.recordPreliminaryWrapper(
                first, "First[example:first]", startEntries, startPost, owner);
        sequence.recordPreliminaryWrapper(
                second, "Second[example:second]", tasks(0), tasks(0), owner);
        assertEquals(0, startEntries.clearCalls);
        assertEquals(0, startPost.clearCalls);
        assertEquals(2, sequence.finishStart(owner));

        sequence.beginEnd(owner);
        observePhase(
                sequence, owner, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES,
                first, "First[example:first]", second, "Second[example:second]");
        observePhase(
                sequence, owner, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS,
                first, "First[example:first]", second, "Second[example:second]");
        observePhase(
                sequence, owner, Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS,
                first, "First[example:first]", second, "Second[example:second]");
        completeAuthoritative(
                sequence, first, "First[example:first]", tasks(2), owner);
        completeAuthoritative(
                sequence, second, "Second[example:second]", tasks(1), owner);
        assertEquals(
                new Mm2JeiDeferredTaskSequence.Summary(
                        2, 3, 2, 2, 2,
                        "3e8b37f5249fc11ee8239069f06407032c5b864dac9cd1f6e9ddf9723ad4334f"),
                sequence.finishEnd(owner));
        sequence.requireComplete();
        assertEquals(Mm2JeiDeferredTaskSequence.State.COMPLETE, sequence.state());
    }

    @Test
    void rejectsPopulatedStartQueuesWithoutClearingThem() {
        Mm2JeiDeferredTaskSequence sequence = new Mm2JeiDeferredTaskSequence();
        Thread owner = Thread.currentThread();
        ClearTrackingList<Runnable> populated = new ClearTrackingList<>();
        populated.add(TASK);
        sequence.beginStart(owner);

        assertThrows(
                IllegalStateException.class,
                () -> sequence.recordPreliminaryWrapper(
                        WRAPPER, PROVIDER, tasks(0), populated, owner));
        assertEquals(1, populated.size());
        assertEquals(0, populated.clearCalls);
        assertEquals(Mm2JeiDeferredTaskSequence.State.FAILED, sequence.state());
    }

    @Test
    void requiresEveryQueueProducerExactlyOnceAndOnlyDuringEnd() {
        Thread owner = Thread.currentThread();
        Mm2JeiDeferredTaskSequence premature = new Mm2JeiDeferredTaskSequence();
        premature.beginStart(owner);
        assertThrows(
                IllegalStateException.class,
                () -> premature.observeProducer(
                        WRAPPER, PROVIDER,
                        Mm2JeiDeferredTaskSequence.Producer.DISPLAYS, owner));

        Mm2JeiDeferredTaskSequence outOfOrder = oneWrapperAtEnd();
        assertThrows(
                IllegalStateException.class,
                () -> outOfOrder.observeProducer(
                        WRAPPER, PROVIDER,
                        Mm2JeiDeferredTaskSequence.Producer.DISPLAYS, owner));

        Mm2JeiDeferredTaskSequence skippedDisplays = oneWrapperAtEnd();
        skippedDisplays.observeProducer(
                WRAPPER, PROVIDER, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner);
        assertThrows(
                IllegalStateException.class,
                () -> skippedDisplays.observeProducer(
                        WRAPPER, PROVIDER,
                        Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS, owner));

        Mm2JeiDeferredTaskSequence duplicate = oneWrapperAtEnd();
        duplicate.observeProducer(
                WRAPPER, PROVIDER, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner);
        assertThrows(
                IllegalStateException.class,
                () -> duplicate.observeProducer(
                        WRAPPER, PROVIDER,
                        Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner));

        Mm2JeiDeferredTaskSequence missing = oneWrapperAtEnd();
        missing.observeProducer(
                WRAPPER, PROVIDER, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner);
        missing.observeProducer(
                WRAPPER, PROVIDER, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS, owner);
        assertThrows(
                IllegalStateException.class,
                () -> missing.beginAuthoritative(
                        WRAPPER, PROVIDER, tasks(0), tasks(4), owner));
    }

    @Test
    void rejectsIdentityPermutationIndependentlyForEveryGlobalEndPhase() {
        TwoWrapperEnd categories = twoWrapperAtEnd("first", "second");
        assertThrows(
                IllegalStateException.class,
                () -> categories.sequence().observeProducer(
                        categories.second(), categories.secondProvider(),
                        Mm2JeiDeferredTaskSequence.Producer.CATEGORIES,
                        categories.owner()));

        TwoWrapperEnd displays = twoWrapperAtEnd("first", "second");
        observePhase(displays, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES);
        assertThrows(
                IllegalStateException.class,
                () -> displays.sequence().observeProducer(
                        displays.second(), displays.secondProvider(),
                        Mm2JeiDeferredTaskSequence.Producer.DISPLAYS,
                        displays.owner()));

        TwoWrapperEnd transfers = twoWrapperAtEnd("first", "second");
        observePhase(transfers, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES);
        observePhase(transfers, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS);
        assertThrows(
                IllegalStateException.class,
                () -> transfers.sequence().observeProducer(
                        transfers.second(), transfers.secondProvider(),
                        Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS,
                        transfers.owner()));

        TwoWrapperEnd postStage = twoWrapperAtEnd("first", "second");
        observePhase(postStage, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES);
        observePhase(postStage, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS);
        observePhase(postStage, Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS);
        assertThrows(
                IllegalStateException.class,
                () -> postStage.sequence().beginAuthoritative(
                        postStage.second(), postStage.secondProvider(),
                        tasks(0), tasks(1), postStage.owner()));
    }

    @Test
    void duplicateProviderNamesRemainDistinctByIdentityAndHashInOrder() {
        TwoWrapperEnd fixture = twoWrapperAtEnd("duplicate", "duplicate");
        observePhase(fixture, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES);
        observePhase(fixture, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS);
        observePhase(fixture, Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS);
        completeAuthoritative(
                fixture.sequence(), fixture.first(), fixture.firstProvider(),
                tasks(1), fixture.owner());
        completeAuthoritative(
                fixture.sequence(), fixture.second(), fixture.secondProvider(),
                tasks(2), fixture.owner());

        Mm2JeiDeferredTaskSequence.Summary summary =
                fixture.sequence().finishEnd(fixture.owner());
        assertEquals(2, summary.wrapperCount());
        assertEquals(3, summary.authoritativePostTaskCount());
        assertEquals(
                "6d553237640afaebece7ec4dcb9c28c358135dd2fb21914ffa6d8db2259eef01",
                summary.providerOrderSha256());
    }

    @Test
    void endHeadAcceptsAuthoritativePostTasksButRequiresEmptyEntryRegistry() {
        Thread owner = Thread.currentThread();
        Mm2JeiDeferredTaskSequence accepted = oneWrapperAtEnd();
        observeAll(accepted, WRAPPER, PROVIDER, owner);
        completeAuthoritative(accepted, WRAPPER, PROVIDER, tasks(50), owner);
        Mm2JeiDeferredTaskSequence.Summary summary = accepted.finishEnd(owner);
        assertEquals(1, summary.wrapperCount());
        assertEquals(50, summary.authoritativePostTaskCount());
        assertEquals(1, summary.registerCategoriesCallbacks());
        assertEquals(1, summary.registerDisplaysCallbacks());
        assertEquals(1, summary.registerTransferHandlersCallbacks());
        assertTrue(summary.providerOrderSha256().matches("[0-9a-f]{64}"));

        Mm2JeiDeferredTaskSequence populatedEntries = oneWrapperAtEnd();
        observeAll(populatedEntries, WRAPPER, PROVIDER, owner);
        assertThrows(
                IllegalStateException.class,
                () -> populatedEntries.beginAuthoritative(
                        WRAPPER, PROVIDER, tasks(1), tasks(2), owner));
    }

    @Test
    void rejectsWrapperIdentityProviderDriftAndMissingEndWrapper() {
        Thread owner = Thread.currentThread();
        Mm2JeiDeferredTaskSequence identityDrift = oneWrapperAtEnd();
        assertThrows(
                IllegalStateException.class,
                () -> identityDrift.observeProducer(
                        new Object(), PROVIDER,
                        Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner));

        Mm2JeiDeferredTaskSequence providerDrift = oneWrapperAtEnd();
        assertThrows(
                IllegalStateException.class,
                () -> providerDrift.observeProducer(
                        WRAPPER, "Different[example:different]",
                        Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner));

        Mm2JeiDeferredTaskSequence missingEnd = oneWrapperAtEnd();
        assertThrows(IllegalStateException.class, () -> missingEnd.finishEnd(owner));
    }

    @Test
    void rejectsQueuesRemainingAfterEndAndNullPostTasks() {
        Thread owner = Thread.currentThread();
        Mm2JeiDeferredTaskSequence remaining = oneWrapperAtEnd();
        observeAll(remaining, WRAPPER, PROVIDER, owner);
        List<Runnable> post = tasks(1);
        remaining.beginAuthoritative(WRAPPER, PROVIDER, tasks(0), post, owner);
        assertThrows(
                IllegalStateException.class,
                () -> remaining.finishAuthoritative(
                        WRAPPER, PROVIDER, tasks(0), post, owner));

        Mm2JeiDeferredTaskSequence nullTask = oneWrapperAtEnd();
        observeAll(nullTask, WRAPPER, PROVIDER, owner);
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(null);
        assertThrows(
                IllegalStateException.class,
                () -> nullTask.beginAuthoritative(
                        WRAPPER, PROVIDER, tasks(0), invalid, owner));
    }

    @Test
    void rejectsThreadDriftAndDuplicateStartCallback() {
        Thread owner = Thread.currentThread();
        Mm2JeiDeferredTaskSequence threadDrift = new Mm2JeiDeferredTaskSequence();
        threadDrift.beginStart(owner);
        assertThrows(
                IllegalStateException.class,
                () -> threadDrift.recordPreliminaryWrapper(
                        WRAPPER, PROVIDER, tasks(0), tasks(0), new Thread("not-owner")));

        Mm2JeiDeferredTaskSequence duplicateStart = new Mm2JeiDeferredTaskSequence();
        duplicateStart.beginStart(owner);
        duplicateStart.recordPreliminaryWrapper(
                WRAPPER, PROVIDER, tasks(0), tasks(0), owner);
        assertThrows(
                IllegalStateException.class,
                () -> duplicateStart.recordPreliminaryWrapper(
                        WRAPPER, PROVIDER, tasks(0), tasks(0), owner));
    }

    private record TwoWrapperEnd(
            Mm2JeiDeferredTaskSequence sequence,
            Object first,
            Object second,
            String firstProvider,
            String secondProvider,
            Thread owner
    ) {
    }

    private static TwoWrapperEnd twoWrapperAtEnd(
            String firstProvider,
            String secondProvider
    ) {
        Mm2JeiDeferredTaskSequence sequence = new Mm2JeiDeferredTaskSequence();
        Thread owner = Thread.currentThread();
        Object first = new Object();
        Object second = new Object();
        sequence.beginStart(owner);
        sequence.recordPreliminaryWrapper(
                first, firstProvider, tasks(0), tasks(0), owner);
        sequence.recordPreliminaryWrapper(
                second, secondProvider, tasks(0), tasks(0), owner);
        sequence.finishStart(owner);
        sequence.beginEnd(owner);
        return new TwoWrapperEnd(
                sequence, first, second, firstProvider, secondProvider, owner);
    }

    private static void observePhase(
            TwoWrapperEnd fixture,
            Mm2JeiDeferredTaskSequence.Producer producer
    ) {
        observePhase(
                fixture.sequence(), fixture.owner(), producer,
                fixture.first(), fixture.firstProvider(),
                fixture.second(), fixture.secondProvider());
    }

    private static void observePhase(
            Mm2JeiDeferredTaskSequence sequence,
            Thread owner,
            Mm2JeiDeferredTaskSequence.Producer producer,
            Object first,
            String firstProvider,
            Object second,
            String secondProvider
    ) {
        sequence.observeProducer(first, firstProvider, producer, owner);
        sequence.observeProducer(second, secondProvider, producer, owner);
    }

    private static Mm2JeiDeferredTaskSequence oneWrapperAtEnd() {
        Mm2JeiDeferredTaskSequence sequence = new Mm2JeiDeferredTaskSequence();
        Thread owner = Thread.currentThread();
        sequence.beginStart(owner);
        sequence.recordPreliminaryWrapper(
                WRAPPER, PROVIDER, tasks(0), tasks(0), owner);
        sequence.finishStart(owner);
        sequence.beginEnd(owner);
        return sequence;
    }

    private static void observeAll(
            Mm2JeiDeferredTaskSequence sequence,
            Object wrapper,
            String provider,
            Thread owner
    ) {
        sequence.observeProducer(
                wrapper, provider, Mm2JeiDeferredTaskSequence.Producer.CATEGORIES, owner);
        sequence.observeProducer(
                wrapper, provider, Mm2JeiDeferredTaskSequence.Producer.DISPLAYS, owner);
        sequence.observeProducer(
                wrapper, provider,
                Mm2JeiDeferredTaskSequence.Producer.TRANSFER_HANDLERS, owner);
    }

    private static void completeAuthoritative(
            Mm2JeiDeferredTaskSequence sequence,
            Object wrapper,
            String provider,
            List<Runnable> post,
            Thread owner
    ) {
        List<Runnable> entryRegistry = tasks(0);
        sequence.beginAuthoritative(wrapper, provider, entryRegistry, post, owner);
        post.clear();
        sequence.finishAuthoritative(wrapper, provider, entryRegistry, post, owner);
    }

    private static List<Runnable> tasks(int count) {
        List<Runnable> tasks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            tasks.add(TASK);
        }
        return tasks;
    }

    private static final class ClearTrackingList<E> extends ArrayList<E> {
        private int clearCalls;

        @Override
        public void clear() {
            clearCalls++;
            super.clear();
        }
    }
}
