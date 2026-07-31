package com.recipetree.reiexport118.compat;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coordinates a reloadable lazily published value with concurrent readers.
 *
 * <p>Readers hold a shared lease for their complete use of the published value. Reloads take the
 * exclusive lease, invalidate the previous generation before invoking the reload body, and publish
 * either a fresh uninitialized generation or a permanent failure. The volatile lifecycle snapshot
 * is both the generation/state authority and the acquire/release fence for the otherwise plain
 * upstream target field.</p>
 */
public final class ReloadCoordinatedPublication<T> {
    private enum Phase {
        READY_UNINITIALIZED,
        BUILDING,
        READY_PUBLISHED,
        RELOADING,
        FAILED
    }

    private static final class Lifecycle<T> {
        private final Phase phase;
        private final long generation;
        private final T published;
        private final Thread owner;
        private final Throwable failure;

        private Lifecycle(
                Phase phase,
                long generation,
                T published,
                Thread owner,
                Throwable failure
        ) {
            this.phase = phase;
            this.generation = generation;
            this.published = published;
            this.owner = owner;
            this.failure = failure;
        }
    }

    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private final Lock tooltipReadLock = lifecycleLock.readLock();
    private final Lock reloadWriteLock = lifecycleLock.writeLock();
    private final Object initializationLock = new Object();
    private volatile Lifecycle<T> lifecycle = new Lifecycle<>(
            Phase.READY_UNINITIALIZED, 0L, null, null, null);
    private volatile boolean armed;

    /** Arms tooltip use only after the exact compatibility preflight and initial reload completed. */
    public void arm() {
        tooltipReadLock.lock();
        try {
            synchronized (initializationLock) {
                Lifecycle<T> current = lifecycle;
                if (current.phase == Phase.FAILED) {
                    throw failureException("cannot arm a failed reload lifecycle", current.failure);
                }
                if (current.generation < 1L) {
                    throw failLocked(
                            "the exact reload wrapper did not observe KubeJS's initial client-script reload",
                            null
                    );
                }
                if (current.phase != Phase.READY_UNINITIALIZED
                        && current.phase != Phase.READY_PUBLISHED) {
                    throw failLocked(
                            "cannot arm tooltip use while lifecycle phase is " + current.phase,
                            null
                    );
                }
                armed = true;
            }
        } finally {
            tooltipReadLock.unlock();
        }
    }

    /** Acquires the shared tooltip lease; callers must pair it with {@link #endTooltipReadLease()}. */
    public void beginTooltipReadLease() {
        if (lifecycleLock.isWriteLockedByCurrentThread()) {
            throw recordFailure(
                    "reload-thread reentrancy attempted to execute an item-tooltip callback",
                    null
            );
        }

        tooltipReadLock.lock();
        boolean acquired = false;
        try {
            Lifecycle<T> current = lifecycle;
            if (!armed) {
                throw recordFailure(
                        "item-tooltip callback ran before exact KubeJS compatibility was armed",
                        null
                );
            }
            if (current.phase == Phase.FAILED) {
                throw failureException("item-tooltip lifecycle is failed", current.failure);
            }
            if (current.phase == Phase.RELOADING) {
                throw recordFailure(
                        "item-tooltip callback entered while its own thread was reloading",
                        null
                );
            }
            acquired = true;
        } finally {
            if (!acquired) {
                tooltipReadLock.unlock();
            }
        }
    }

    /** Releases one shared tooltip lease held by the current thread. */
    public void endTooltipReadLease() {
        if (lifecycleLock.getReadHoldCount() == 0) {
            throw recordFailure(
                    "item-tooltip read lease release was not paired with an acquisition",
                    null
            );
        }
        tooltipReadLock.unlock();
    }

    /** Convenience wrapper for tests and non-hot call sites. */
    public void withTooltipReadLease(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        beginTooltipReadLease();
        try {
            callback.run();
        } catch (RuntimeException | Error exception) {
            recordTooltipFailure(exception);
            throw exception;
        } finally {
            endTooltipReadLease();
        }
    }

    /** Records an error from the protected callback as terminal without replacing its identity. */
    public void recordTooltipFailure(Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        synchronized (initializationLock) {
            Lifecycle<T> current = lifecycle;
            if (current.phase != Phase.FAILED) {
                lifecycle = new Lifecycle<>(
                        Phase.FAILED,
                        current.generation,
                        null,
                        null,
                        failure
                );
            }
        }
    }

    /** Rejects requests after lifecycle failure or before the exact preflight arms tooltip use. */
    public void requireHealthy() {
        tooltipReadLock.lock();
        try {
            synchronized (initializationLock) {
                Lifecycle<T> current = lifecycle;
                if (current.phase == Phase.FAILED) {
                    throw failureException(
                            "KubeJS tooltip/reload lifecycle is failed", current.failure);
                }
                if (!armed) {
                    throw failLocked(
                            "KubeJS tooltip/reload lifecycle health was checked before arming",
                            null
                    );
                }
                if (current.phase != Phase.READY_UNINITIALIZED
                        && current.phase != Phase.READY_PUBLISHED) {
                    throw failLocked(
                            "KubeJS tooltip/reload lifecycle health observed phase " + current.phase,
                            null
                    );
                }
            }
        } finally {
            tooltipReadLock.unlock();
        }
    }

    /** Returns the exact generation value, building and publishing it once when needed. */
    public T ensureInitialized(
            Supplier<? extends T> currentReader,
            Supplier<? extends T> builder,
            Consumer<? super T> publisher
    ) {
        Objects.requireNonNull(currentReader, "currentReader");
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(publisher, "publisher");
        if (lifecycleLock.getReadHoldCount() == 0) {
            throw recordFailure(
                    "item-tooltip publication was requested without a tooltip read lease",
                    null
            );
        }
        if (!armed) {
            throw recordFailure(
                    "item-tooltip publication was requested before compatibility was armed",
                    null
            );
        }

        Lifecycle<T> fast = lifecycle;
        if (fast.phase == Phase.READY_PUBLISHED) {
            T upstream = readCurrentOrFail(currentReader, "reading published tooltip target");
            if (upstream == fast.published) {
                return fast.published;
            }
        }

        synchronized (initializationLock) {
            Lifecycle<T> current = lifecycle;
            if (current.phase == Phase.FAILED) {
                throw failureException("item-tooltip publication lifecycle is failed", current.failure);
            }
            if (current.phase == Phase.READY_PUBLISHED) {
                T upstream = readCurrentOrFail(currentReader, "validating published tooltip target");
                if (upstream == current.published) {
                    return current.published;
                }
                throw failLocked(
                        "published KubeJS tooltip target changed outside a tracked reload",
                        null
                );
            }
            if (current.phase == Phase.BUILDING) {
                if (current.owner == Thread.currentThread()) {
                    throw failLocked(
                            "recursive item-tooltip publication attempted while the same thread was building",
                            null
                    );
                }
                throw failLocked(
                        "item-tooltip publication observed an unexpected concurrent builder",
                        null
                );
            }
            if (current.phase != Phase.READY_UNINITIALIZED) {
                throw failLocked(
                        "item-tooltip publication entered invalid lifecycle phase " + current.phase,
                        null
                );
            }
            if (readCurrentOrFail(currentReader, "validating uninitialized tooltip target") != null) {
                throw failLocked(
                        "uninitialized KubeJS tooltip generation contains an externally published value",
                        null
                );
            }

            Thread owner = Thread.currentThread();
            lifecycle = new Lifecycle<>(
                    Phase.BUILDING, current.generation, null, owner, null);
            try {
                T candidate = Objects.requireNonNull(
                        builder.get(),
                        "exact item-tooltip publication builder returned null"
                );
                Lifecycle<T> afterBuild = lifecycle;
                if (afterBuild.phase != Phase.BUILDING || afterBuild.owner != owner
                        || afterBuild.generation != current.generation) {
                    throw failureException(
                            "item-tooltip builder lost lifecycle ownership",
                            afterBuild.failure
                    );
                }
                publisher.accept(candidate);
                if (readCurrentOrFail(currentReader, "verifying tooltip target publication")
                        != candidate) {
                    throw new IllegalStateException(
                            "target publisher did not retain the exact completely built candidate"
                    );
                }
                lifecycle = new Lifecycle<>(
                        Phase.READY_PUBLISHED,
                        current.generation,
                        candidate,
                        null,
                        null
                );
                return candidate;
            } catch (RuntimeException | Error exception) {
                if (lifecycle.phase != Phase.FAILED) {
                    lifecycle = new Lifecycle<>(
                            Phase.FAILED,
                            current.generation,
                            null,
                            null,
                            exception
                    );
                }
                throw exception;
            }
        }
    }

    /**
     * Runs the exact upstream reset/unload/load body under the exclusive lifecycle lease.
     * Initial KubeJS startup reloads are intentionally allowed before {@link #arm()}.
     */
    public void runReload(Supplier<? extends T> currentReader, Runnable reloadBody) {
        Objects.requireNonNull(currentReader, "currentReader");
        Objects.requireNonNull(reloadBody, "reloadBody");
        if (lifecycleLock.getReadHoldCount() > 0) {
            throw recordFailure(
                    "item-tooltip thread attempted a read-to-write reload lock upgrade",
                    null
            );
        }
        if (lifecycleLock.isWriteLockedByCurrentThread()) {
            throw recordFailure("recursive client-script reload attempted", null);
        }

        reloadWriteLock.lock();
        try {
            final long reloadGeneration;
            synchronized (initializationLock) {
                Lifecycle<T> current = lifecycle;
                if (current.phase == Phase.FAILED) {
                    throw failureException("client-script reload lifecycle is failed", current.failure);
                }
                if (current.phase == Phase.READY_PUBLISHED) {
                    if (readCurrentOrFail(currentReader, "validating tooltip target before reload")
                            != current.published) {
                        throw failLocked(
                                "published KubeJS tooltip target changed before tracked reload",
                                null
                        );
                    }
                } else if (current.phase == Phase.READY_UNINITIALIZED) {
                    if (readCurrentOrFail(currentReader, "validating empty target before reload")
                            != null) {
                        throw failLocked(
                                "uninitialized KubeJS tooltip generation contains an external value",
                                null
                        );
                    }
                } else {
                    throw failLocked(
                            "client-script reload entered invalid lifecycle phase " + current.phase,
                            null
                    );
                }
                try {
                    reloadGeneration = Math.addExact(current.generation, 1L);
                } catch (ArithmeticException exception) {
                    throw failLocked("KubeJS reload generation overflowed", exception);
                }
                lifecycle = new Lifecycle<>(
                        Phase.RELOADING,
                        reloadGeneration,
                        null,
                        Thread.currentThread(),
                        null
                );
            }

            try {
                reloadBody.run();
                synchronized (initializationLock) {
                    Lifecycle<T> afterReload = lifecycle;
                    if (afterReload.phase != Phase.RELOADING
                            || afterReload.owner != Thread.currentThread()
                            || afterReload.generation != reloadGeneration) {
                        throw failureException(
                                "client-script reload lost lifecycle ownership",
                                afterReload.failure
                        );
                    }
                    if (readCurrentOrFail(currentReader, "verifying tooltip reset after reload")
                            != null) {
                        throw new IllegalStateException(
                                "successful tracked KubeJS reload did not leave tooltip target null"
                        );
                    }
                    lifecycle = new Lifecycle<>(
                            Phase.READY_UNINITIALIZED,
                            reloadGeneration,
                            null,
                            null,
                            null
                    );
                }
            } catch (RuntimeException | Error exception) {
                synchronized (initializationLock) {
                    if (lifecycle.phase != Phase.FAILED) {
                        lifecycle = new Lifecycle<>(
                                Phase.FAILED,
                                reloadGeneration,
                                null,
                                null,
                                exception
                        );
                    }
                }
                throw exception;
            }
        } finally {
            reloadWriteLock.unlock();
        }
    }

    private IllegalStateException recordFailure(String message, Throwable cause) {
        synchronized (initializationLock) {
            return failLocked(message, cause);
        }
    }

    private T readCurrentOrFail(Supplier<? extends T> currentReader, String context) {
        try {
            return currentReader.get();
        } catch (RuntimeException | Error exception) {
            throw recordFailure(context + " failed", exception);
        }
    }

    private IllegalStateException failLocked(String message, Throwable cause) {
        Lifecycle<T> current = lifecycle;
        Throwable recorded = cause != null ? cause : new IllegalStateException(message);
        if (current.phase != Phase.FAILED) {
            lifecycle = new Lifecycle<>(
                    Phase.FAILED,
                    current.generation,
                    null,
                    null,
                    recorded
            );
        } else {
            recorded = current.failure;
        }
        return failureException(message, recorded);
    }

    private static IllegalStateException failureException(String message, Throwable cause) {
        return new IllegalStateException(message, cause);
    }
}
