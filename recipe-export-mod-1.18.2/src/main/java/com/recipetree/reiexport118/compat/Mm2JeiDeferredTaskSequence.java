package com.recipetree.reiexport118.compat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure fail-closed state machine for the pinned JEI compatibility wrapper's deferred queues.
 *
 * <p>The exact MM2 lifecycle reaches each wrapper's {@code postStage} with empty queues at
 * preliminary {@code START}. The audited queue-producing callbacks then run once during
 * authoritative {@code END}. Their pinned REI registry order is categories, displays, transfer
 * handlers, then {@code postStage(END)} HEAD/RETURN, which consumes and clears their tasks.</p>
 */
final class Mm2JeiDeferredTaskSequence {
    enum State {
        INACTIVE,
        START,
        BETWEEN_STAGES,
        END,
        COMPLETE,
        FAILED
    }

    enum Producer {
        CATEGORIES,
        DISPLAYS,
        TRANSFER_HANDLERS
    }

    private enum WrapperPhase {
        REGISTER_CATEGORIES,
        REGISTER_DISPLAYS,
        REGISTER_TRANSFER_HANDLERS,
        POST_STAGE_HEAD,
        POST_STAGE_RETURN,
        COMPLETE
    }

    record Summary(
            int wrapperCount,
            int authoritativePostTaskCount,
            int registerCategoriesCallbacks,
            int registerDisplaysCallbacks,
            int registerTransferHandlersCallbacks,
            String providerOrderSha256
    ) {
    }

    private static final class WrapperRecord {
        private final String providerName;
        private int registerCategoriesCallbacks;
        private int registerDisplaysCallbacks;
        private int registerTransferHandlersCallbacks;
        private int authoritativePostTasks = -1;
        private boolean authoritativeStarted;
        private boolean authoritativeFinished;
        private WrapperPhase phase = WrapperPhase.REGISTER_CATEGORIES;

        private WrapperRecord(String providerName) {
            this.providerName = providerName;
        }
    }

    private final Map<Object, WrapperRecord> wrappers = new IdentityHashMap<>();
    private final List<Object> wrapperOrder = new ArrayList<>();
    private State state = State.INACTIVE;
    private Thread ownerThread;
    private int registerCategoriesCursor;
    private int registerDisplaysCursor;
    private int registerTransferHandlersCursor;
    private int postStageHeadCursor;

    synchronized void beginStart(Thread thread) {
        requireState(State.INACTIVE, "begin START");
        ownerThread = requireThread(thread);
        state = State.START;
    }

    synchronized void recordPreliminaryWrapper(
            Object wrapper,
            String providerName,
            List<?> entryRegistry,
            List<?> post,
            Thread thread
    ) {
        requireState(State.START, "record preliminary wrapper");
        requireOwner(thread);
        requireWrapper(wrapper);
        String exactProviderName = requireProviderName(providerName);
        requireQueue(entryRegistry, exactProviderName, "entryRegistry");
        requireQueue(post, exactProviderName, "post");
        requireQueuesEmpty(entryRegistry, post, exactProviderName, "preliminary START");
        if (wrappers.containsKey(wrapper)) {
            reject("JEI wrapper reached START postStage more than once: " + exactProviderName);
        }
        wrappers.put(wrapper, new WrapperRecord(exactProviderName));
        wrapperOrder.add(wrapper);
    }

    synchronized int finishStart(Thread thread) {
        requireState(State.START, "finish START");
        requireOwner(thread);
        if (wrappers.isEmpty()) {
            reject("owned START observed no pinned JEI plugin wrappers");
        }
        state = State.BETWEEN_STAGES;
        return wrappers.size();
    }

    synchronized void beginEnd(Thread thread) {
        requireState(State.BETWEEN_STAGES, "begin END");
        requireOwner(thread);
        state = State.END;
    }

    synchronized void observeProducer(
            Object wrapper,
            String providerName,
            Producer producer,
            Thread thread
    ) {
        requireState(State.END, "observe " + producer + " queue producer");
        requireOwner(thread);
        requireWrapper(wrapper);
        String exactProviderName = requireProviderName(providerName);
        if (producer == null) {
            reject("JEI queue producer must not be null for " + exactProviderName);
        }
        WrapperRecord record = requireKnownWrapper(wrapper, exactProviderName);
        if (record.authoritativeStarted) {
            reject("JEI queue producer ran after authoritative postStage HEAD provider="
                    + exactProviderName + ", producer=" + producer);
        }
        switch (producer) {
            case CATEGORIES -> {
                requireTraversalIdentity(
                        wrapper, exactProviderName, registerCategoriesCursor,
                        "registerCategories");
                requirePhase(
                        record, WrapperPhase.REGISTER_CATEGORIES,
                        exactProviderName, "registerCategories");
                requireFirstCallback(
                        record.registerCategoriesCallbacks, exactProviderName, producer);
                record.registerCategoriesCallbacks = 1;
                record.phase = WrapperPhase.REGISTER_DISPLAYS;
                registerCategoriesCursor++;
            }
            case DISPLAYS -> {
                requireCursorComplete(
                        registerCategoriesCursor, "registerCategories",
                        "registerDisplays");
                requireTraversalIdentity(
                        wrapper, exactProviderName, registerDisplaysCursor,
                        "registerDisplays");
                requirePhase(
                        record, WrapperPhase.REGISTER_DISPLAYS,
                        exactProviderName, "registerDisplays");
                requireFirstCallback(
                        record.registerDisplaysCallbacks, exactProviderName, producer);
                record.registerDisplaysCallbacks = 1;
                record.phase = WrapperPhase.REGISTER_TRANSFER_HANDLERS;
                registerDisplaysCursor++;
            }
            case TRANSFER_HANDLERS -> {
                requireCursorComplete(
                        registerDisplaysCursor, "registerDisplays",
                        "registerTransferHandlers");
                requireTraversalIdentity(
                        wrapper, exactProviderName, registerTransferHandlersCursor,
                        "registerTransferHandlers");
                requirePhase(
                        record, WrapperPhase.REGISTER_TRANSFER_HANDLERS,
                        exactProviderName, "registerTransferHandlers");
                requireFirstCallback(
                        record.registerTransferHandlersCallbacks, exactProviderName, producer);
                record.registerTransferHandlersCallbacks = 1;
                record.phase = WrapperPhase.POST_STAGE_HEAD;
                registerTransferHandlersCursor++;
            }
        }
    }

    synchronized void beginAuthoritative(
            Object wrapper,
            String providerName,
            List<?> entryRegistry,
            List<?> post,
            Thread thread
    ) {
        requireState(State.END, "enter authoritative wrapper postStage");
        requireOwner(thread);
        requireWrapper(wrapper);
        String exactProviderName = requireProviderName(providerName);
        WrapperRecord record = requireKnownWrapper(wrapper, exactProviderName);
        if (record.authoritativeStarted) {
            reject("JEI wrapper reached END postStage more than once: " + exactProviderName);
        }
        requireCursorComplete(
                registerTransferHandlersCursor, "registerTransferHandlers",
                "postStage HEAD");
        requireTraversalIdentity(
                wrapper, exactProviderName, postStageHeadCursor, "postStage HEAD");
        requirePhase(
                record, WrapperPhase.POST_STAGE_HEAD,
                exactProviderName, "postStage HEAD");
        requireExactlyOneProducerCallback(record, exactProviderName);
        requireQueue(entryRegistry, exactProviderName, "entryRegistry");
        requireQueue(post, exactProviderName, "post");
        if (!entryRegistry.isEmpty()) {
            reject("JEI entryRegistry queue was not empty at authoritative END HEAD provider="
                    + exactProviderName + ", tasks=" + entryRegistry.size());
        }
        record.authoritativePostTasks = post.size();
        record.authoritativeStarted = true;
        record.phase = WrapperPhase.POST_STAGE_RETURN;
        postStageHeadCursor++;
    }

    synchronized void finishAuthoritative(
            Object wrapper,
            String providerName,
            List<?> entryRegistry,
            List<?> post,
            Thread thread
    ) {
        requireState(State.END, "exit authoritative wrapper postStage");
        requireOwner(thread);
        requireWrapper(wrapper);
        String exactProviderName = requireProviderName(providerName);
        WrapperRecord record = requireKnownWrapper(wrapper, exactProviderName);
        requirePhase(
                record, WrapperPhase.POST_STAGE_RETURN,
                exactProviderName, "postStage RETURN");
        if (!record.authoritativeStarted || record.authoritativeFinished) {
            reject("JEI wrapper END completion sequence drift provider=" + exactProviderName
                    + ", started=" + record.authoritativeStarted
                    + ", finished=" + record.authoritativeFinished);
        }
        requireQueue(entryRegistry, exactProviderName, "entryRegistry");
        requireQueue(post, exactProviderName, "post");
        requireQueuesEmpty(entryRegistry, post, exactProviderName, "authoritative END return");
        record.authoritativeFinished = true;
        record.phase = WrapperPhase.COMPLETE;
    }

    synchronized Summary finishEnd(Thread thread) {
        requireState(State.END, "finish END");
        requireOwner(thread);
        requireAllTraversalCursorsComplete();
        List<String> incomplete = wrappers.values().stream()
                .filter(record -> record.phase != WrapperPhase.COMPLETE
                        || !record.authoritativeStarted || !record.authoritativeFinished)
                .map(record -> record.providerName)
                .sorted()
                .toList();
        if (!incomplete.isEmpty()) {
            reject("authoritative END wrapper identity set did not match START; incomplete="
                    + incomplete);
        }
        Summary summary = summary();
        if (summary.registerCategoriesCallbacks() != summary.wrapperCount()
                || summary.registerDisplaysCallbacks() != summary.wrapperCount()
                || summary.registerTransferHandlersCallbacks() != summary.wrapperCount()) {
            reject("authoritative END queue-producer totals did not equal wrapper total: "
                    + summary);
        }
        state = State.COMPLETE;
        return summary;
    }

    synchronized void requireComplete() {
        requireState(State.COMPLETE, "authorize export");
    }

    synchronized State state() {
        return state;
    }

    synchronized void fail(Throwable failure) {
        if (failure == null) {
            throw new IllegalArgumentException("deferred-task failure must not be null");
        }
        state = State.FAILED;
    }

    private Summary summary() {
        int postTasks = 0;
        int categoriesCallbacks = 0;
        int displaysCallbacks = 0;
        int transferCallbacks = 0;
        for (Object wrapper : wrapperOrder) {
            WrapperRecord record = wrappers.get(wrapper);
            postTasks = Math.addExact(postTasks, record.authoritativePostTasks);
            categoriesCallbacks = Math.addExact(
                    categoriesCallbacks, record.registerCategoriesCallbacks);
            displaysCallbacks = Math.addExact(
                    displaysCallbacks, record.registerDisplaysCallbacks);
            transferCallbacks = Math.addExact(
                    transferCallbacks, record.registerTransferHandlersCallbacks);
        }
        return new Summary(
                wrappers.size(), postTasks, categoriesCallbacks,
                displaysCallbacks, transferCallbacks, providerOrderSha256());
    }

    private String providerOrderSha256() {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        for (Object wrapper : wrapperOrder) {
            byte[] provider = wrappers.get(wrapper).providerName
                    .getBytes(StandardCharsets.UTF_8);
            digest.update((byte) (provider.length >>> 24));
            digest.update((byte) (provider.length >>> 16));
            digest.update((byte) (provider.length >>> 8));
            digest.update((byte) provider.length);
            digest.update(provider);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void requireTraversalIdentity(
            Object wrapper,
            String providerName,
            int cursor,
            String callback
    ) {
        if (cursor >= wrapperOrder.size()) {
            reject("JEI authoritative END traversal contained an extra wrapper callback="
                    + callback + ", cursor=" + cursor + ", wrapperCount="
                    + wrapperOrder.size() + ", actualProvider=" + providerName
                    + ", actualIdentity=" + System.identityHashCode(wrapper));
        }
        Object expectedWrapper = wrapperOrder.get(cursor);
        if (wrapper != expectedWrapper) {
            WrapperRecord expected = wrappers.get(expectedWrapper);
            reject("JEI authoritative END wrapper traversal permutation callback=" + callback
                    + ", cursor=" + cursor + ", expectedProvider=" + expected.providerName
                    + ", expectedIdentity=" + System.identityHashCode(expectedWrapper)
                    + ", actualProvider=" + providerName
                    + ", actualIdentity=" + System.identityHashCode(wrapper));
        }
    }

    private void requireCursorComplete(
            int cursor,
            String prerequisite,
            String attempted
    ) {
        if (cursor != wrapperOrder.size()) {
            reject("JEI authoritative END global phase order drift: prerequisite="
                    + prerequisite + " cursor=" + cursor + "/" + wrapperOrder.size()
                    + ", attempted=" + attempted);
        }
    }

    private void requireAllTraversalCursorsComplete() {
        int expected = wrapperOrder.size();
        if (registerCategoriesCursor != expected
                || registerDisplaysCursor != expected
                || registerTransferHandlersCursor != expected
                || postStageHeadCursor != expected) {
            reject("JEI authoritative END traversal cursors incomplete wrapperCount=" + expected
                    + ", registerCategories=" + registerCategoriesCursor
                    + ", registerDisplays=" + registerDisplaysCursor
                    + ", registerTransferHandlers=" + registerTransferHandlersCursor
                    + ", postStageHead=" + postStageHeadCursor);
        }
    }

    private WrapperRecord requireKnownWrapper(Object wrapper, String providerName) {
        WrapperRecord record = wrappers.get(wrapper);
        if (record == null) {
            reject("authoritative END introduced a wrapper identity absent from START: "
                    + providerName);
        }
        if (!record.providerName.equals(providerName)) {
            reject("JEI wrapper provider identity drifted between START and END: expected="
                    + record.providerName + ", actual=" + providerName);
        }
        return record;
    }

    private void requireFirstCallback(
            int priorCount,
            String providerName,
            Producer producer
    ) {
        if (priorCount != 0) {
            reject("JEI queue-producing callback ran more than once during authoritative END "
                    + "provider=" + providerName + ", producer=" + producer
                    + ", priorCount=" + priorCount);
        }
    }

    private void requirePhase(
            WrapperRecord record,
            WrapperPhase expected,
            String providerName,
            String callback
    ) {
        if (record.phase != expected) {
            reject("JEI authoritative END callback order drift provider=" + providerName
                    + ", expectedPhase=" + record.phase + ", actualCallback=" + callback);
        }
    }

    private void requireExactlyOneProducerCallback(
            WrapperRecord record,
            String providerName
    ) {
        if (record.registerCategoriesCallbacks != 1
                || record.registerDisplaysCallbacks != 1
                || record.registerTransferHandlersCallbacks != 1) {
            reject("JEI authoritative END queue-producing callback cardinality drift provider="
                    + providerName
                    + ", registerCategories=" + record.registerCategoriesCallbacks
                    + ", registerDisplays=" + record.registerDisplaysCallbacks
                    + ", registerTransferHandlers="
                    + record.registerTransferHandlersCallbacks);
        }
    }

    private void requireQueuesEmpty(
            List<?> entryRegistry,
            List<?> post,
            String providerName,
            String seam
    ) {
        if (!entryRegistry.isEmpty() || !post.isEmpty()) {
            reject("pinned JEI wrapper queues were not empty at " + seam + " provider="
                    + providerName + ", entryRegistry=" + entryRegistry.size()
                    + ", post=" + post.size());
        }
    }

    private void requireQueue(List<?> queue, String providerName, String queueName) {
        if (queue == null) {
            reject("JEI " + queueName + " queue is null for " + providerName);
        }
        for (Object task : queue) {
            if (task == null) {
                reject("JEI " + queueName + " queue contains a null task for " + providerName);
            }
        }
    }

    private void requireState(State expected, String operation) {
        if (state != expected) {
            reject("cannot " + operation + " from deferred-task state " + state
                    + "; expected=" + expected);
        }
    }

    private Thread requireThread(Thread thread) {
        if (thread == null) {
            reject("owned deferred-task thread must not be null");
        }
        return thread;
    }

    private void requireOwner(Thread thread) {
        requireThread(thread);
        if (thread != ownerThread) {
            reject("JEI deferred-task thread identity drift: expected="
                    + ownerThread.getName() + ", actual=" + thread.getName());
        }
    }

    private void requireWrapper(Object wrapper) {
        if (wrapper == null) {
            reject("JEI wrapper identity must not be null");
        }
    }

    private String requireProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            reject("JEI wrapper provider name must not be blank");
        }
        return providerName;
    }

    private void reject(String message) {
        state = State.FAILED;
        throw new IllegalStateException(message);
    }
}
