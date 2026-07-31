package com.recipetree.reiexport118.compat;

import java.util.Objects;

/** Pure fail-closed state machine for one MM2 recipe sync and one owned REI reload. */
final class Mm2ReiLifecycleSequence {
    enum State {
        INACTIVE,
        WAITING_FOR_NATIVE_CALLBACKS,
        OWNED_RELOAD,
        COMPLETE,
        FAILED
    }

    enum NativeStage { START, END }

    enum NativeThreadRole { PACKET, RENDER }

    enum ReloadStage { START, END }

    record Publication(int generation, Object identity) {
        Publication {
            if (generation <= 0 || identity == null) {
                throw new IllegalArgumentException("publication must be complete");
            }
        }

        boolean sameAs(Publication other) {
            return other != null
                    && generation == other.generation
                    && identity == other.identity;
        }
    }

    private State state = State.INACTIVE;
    private Object recipeManager;
    private Thread reloadOwner;
    private boolean packetStart;
    private boolean renderStart;
    private boolean nativeEnd;
    private Thread packetThread;
    private Thread renderThread;
    private boolean reloadStartEntered;
    private boolean reloadStartExited;
    private boolean reloadEndEntered;
    private boolean reloadEndExited;
    private Publication startPublication;

    synchronized void arm() {
        require(state == State.INACTIVE, "lifecycle was armed more than once");
        state = State.WAITING_FOR_NATIVE_CALLBACKS;
    }

    synchronized boolean suppressNative(
            NativeStage stage,
            NativeThreadRole role,
            Object manager,
            Thread thread
    ) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(role, "role");
        require(manager != null, "native REI callback supplied a null RecipeManager");
        require(thread != null, "native REI callback supplied a null thread");
        require(state == State.WAITING_FOR_NATIVE_CALLBACKS,
                "native REI callback entered lifecycle state " + state);
        if (recipeManager == null) {
            recipeManager = manager;
        } else {
            require(recipeManager == manager,
                    "native REI callbacks observed different RecipeManager identities");
        }
        if (stage == NativeStage.START) {
            if (role == NativeThreadRole.PACKET) {
                require(!packetStart && !renderStart && !nativeEnd,
                        "packet-thread native REI START callback was duplicated or reordered");
                packetStart = true;
                packetThread = thread;
            } else {
                require(!renderStart && !nativeEnd,
                        "render-thread native REI START callback was duplicated or reordered");
                if (packetStart) {
                    require(packetThread != thread,
                            "packet and render native REI callbacks used the same thread identity");
                }
                renderStart = true;
                renderThread = thread;
            }
        } else {
            require(role == NativeThreadRole.RENDER,
                    "native REI END callback arrived off the render thread");
            require(renderStart && !nativeEnd,
                    "render-thread native REI END callback arrived before START or was duplicated");
            require(renderThread == thread,
                    "native REI START and END callbacks used different render threads");
            nativeEnd = true;
        }
        return true;
    }

    synchronized void beginOwnedReload(Object activeManager, Thread owner) {
        require(state == State.WAITING_FOR_NATIVE_CALLBACKS,
                "owned reload began in lifecycle state " + state);
        require(renderStart && nativeEnd,
                "owned reload began without exactly one render-thread native START and END callback");
        require(recipeManager == activeManager,
                "active client RecipeManager differs from the suppressed native callbacks");
        require(owner != null, "owned reload has no owner thread");
        require(renderThread == owner,
                "owned reload did not begin on the authoritative native render thread");
        reloadOwner = owner;
        state = State.OWNED_RELOAD;
    }

    synchronized void enterReloadStage(ReloadStage stage, Thread thread) {
        requireOwned(thread);
        if (stage == ReloadStage.START) {
            require(!reloadStartEntered && !reloadStartExited
                            && !reloadEndEntered && !reloadEndExited,
                    "owned REI START stage was duplicated or reordered");
            reloadStartEntered = true;
        } else {
            require(reloadStartEntered && reloadStartExited
                            && !reloadEndEntered && !reloadEndExited,
                    "owned REI END stage arrived before completed START or was duplicated");
            reloadEndEntered = true;
        }
    }

    synchronized void exitReloadStage(
            ReloadStage stage,
            Thread thread,
            Publication publication
    ) {
        requireOwned(thread);
        Objects.requireNonNull(publication, "publication");
        if (stage == ReloadStage.START) {
            require(reloadStartEntered && !reloadStartExited && !reloadEndEntered,
                    "owned REI START exit was duplicated or reordered");
            startPublication = publication;
            reloadStartExited = true;
        } else {
            require(reloadEndEntered && !reloadEndExited,
                    "owned REI END exit was duplicated or reordered");
            require(startPublication.sameAs(publication),
                    "KubeJS handler publication changed between REI START and END");
            reloadEndExited = true;
        }
    }

    synchronized void completeOwnedReload(Thread thread) {
        requireOwned(thread);
        require(reloadStartEntered && reloadStartExited
                        && reloadEndEntered && reloadEndExited,
                "owned reload returned without one complete START and END stage");
        state = State.COMPLETE;
        recipeManager = null;
        reloadOwner = null;
    }

    synchronized void requireComplete() {
        require(state == State.COMPLETE,
                "MM2 export observed incomplete REI lifecycle state " + state);
    }

    synchronized State state() {
        return state;
    }

    synchronized void fail(Throwable cause) {
        state = State.FAILED;
        recipeManager = null;
        reloadOwner = null;
    }

    private void requireOwned(Thread thread) {
        require(state == State.OWNED_RELOAD,
                "REI reload stage entered lifecycle state " + state);
        require(reloadOwner == thread,
                "REI reload stage executed on a foreign thread");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            state = State.FAILED;
            throw new IllegalStateException(message);
        }
    }
}
