package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

final class ReloadCoordinatedPublicationTest {
    @Test
    void initialReloadRunsBeforeArmAndPublicationThenUsesTheFastPath() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();

        AtomicInteger builds = new AtomicInteger();
        Map<String, Integer> first = fixture.withTooltip(() -> fixture.gate.ensureInitialized(
                fixture.target::get,
                () -> Map.of("generation", builds.incrementAndGet()),
                fixture.target::set
        ));
        Map<String, Integer> second = fixture.withTooltip(() -> fixture.gate.ensureInitialized(
                fixture.target::get,
                () -> {
                    throw new AssertionError("published generation rebuilt on the fast path");
                },
                fixture.target::set
        ));

        assertSame(first, second);
        assertSame(first, fixture.target.get());
        assertEquals(1, builds.get());
    }

    @Test
    void activeTooltipDelaysReloadAndNextTooltipBuildsAFreshGeneration() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Fixture fixture = new Fixture();
            fixture.initialReloadAndArm();
            AtomicInteger builds = new AtomicInteger();
            Map<String, Integer> first = fixture.withTooltip(() -> fixture.gate.ensureInitialized(
                    fixture.target::get,
                    () -> Map.of("generation", builds.incrementAndGet()),
                    fixture.target::set
            ));

            CountDownLatch tooltipEntered = new CountDownLatch(1);
            CountDownLatch releaseTooltip = new CountDownLatch(1);
            CountDownLatch reloadAttempted = new CountDownLatch(1);
            CountDownLatch reloadBodyStarted = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> tooltip = workers.submit(() -> fixture.gate.withTooltipReadLease(() -> {
                    assertSame(first, fixture.gate.ensureInitialized(
                            fixture.target::get,
                            () -> {
                                throw new AssertionError("active generation rebuilt");
                            },
                            fixture.target::set
                    ));
                    tooltipEntered.countDown();
                    await(releaseTooltip);
                }));
                tooltipEntered.await();

                Future<?> reload = workers.submit(() -> {
                    reloadAttempted.countDown();
                    fixture.gate.runReload(fixture.target::get, () -> {
                        reloadBodyStarted.countDown();
                        fixture.target.set(null);
                    });
                });
                reloadAttempted.await();
                assertFalse(reloadBodyStarted.await(200, TimeUnit.MILLISECONDS),
                        "reload body overlapped an active tooltip callback");
                releaseTooltip.countDown();
                tooltip.get();
                reload.get();
                assertTrue(reloadBodyStarted.await(1, TimeUnit.SECONDS));

                Map<String, Integer> second = fixture.withTooltip(
                        () -> fixture.gate.ensureInitialized(
                                fixture.target::get,
                                () -> Map.of("generation", builds.incrementAndGet()),
                                fixture.target::set
                        ));
                assertNotSame(first, second);
                assertEquals(2, second.get("generation"));
                assertEquals(2, builds.get());
            } finally {
                releaseTooltip.countDown();
                workers.shutdownNow();
            }
        });
    }

    @Test
    void inProgressReloadBlocksNewTooltipUntilReloadCompletes() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Fixture fixture = new Fixture();
            fixture.initialReloadAndArm();
            CountDownLatch reloadBodyStarted = new CountDownLatch(1);
            CountDownLatch releaseReload = new CountDownLatch(1);
            CountDownLatch tooltipAttempted = new CountDownLatch(1);
            CountDownLatch tooltipEntered = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> reload = workers.submit(() -> fixture.gate.runReload(
                        fixture.target::get,
                        () -> {
                            fixture.target.set(null);
                            reloadBodyStarted.countDown();
                            await(releaseReload);
                        }
                ));
                reloadBodyStarted.await();

                Future<?> tooltip = workers.submit(() -> {
                    tooltipAttempted.countDown();
                    fixture.gate.withTooltipReadLease(tooltipEntered::countDown);
                });
                tooltipAttempted.await();
                assertFalse(tooltipEntered.await(200, TimeUnit.MILLISECONDS),
                        "tooltip callback entered during reload");
                releaseReload.countDown();
                reload.get();
                tooltip.get();
                assertTrue(tooltipEntered.await(1, TimeUnit.SECONDS));
            } finally {
                releaseReload.countDown();
                workers.shutdownNow();
            }
        });
    }

    @Test
    void exceptionalTooltipExitReleasesTheLeaseAndRecordsTheExactTerminalCause() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();
        RuntimeException marker = new RuntimeException("tooltip failure");

        assertSame(marker, assertThrows(RuntimeException.class,
                () -> fixture.gate.withTooltipReadLease(() -> {
                    throw marker;
                })));
        IllegalStateException health = assertThrows(
                IllegalStateException.class, fixture.gate::requireHealthy);
        assertSame(marker, health.getCause());
        IllegalStateException reload = assertThrows(
                IllegalStateException.class,
                () -> fixture.gate.runReload(
                        fixture.target::get,
                        () -> fixture.target.set(null)
                ));
        assertSame(marker, reload.getCause());
    }

    @Test
    void healthCheckWaitsForHealthyColdBuildInsteadOfPoisoningIt() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            Fixture fixture = new Fixture();
            fixture.initialReloadAndArm();
            CountDownLatch builderStarted = new CountDownLatch(1);
            CountDownLatch releaseBuilder = new CountDownLatch(1);
            CountDownLatch healthAttempted = new CountDownLatch(1);
            CountDownLatch healthReturned = new CountDownLatch(1);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<?> tooltip = workers.submit(() -> fixture.gate.withTooltipReadLease(
                        () -> fixture.gate.ensureInitialized(
                                fixture.target::get,
                                () -> {
                                    builderStarted.countDown();
                                    await(releaseBuilder);
                                    return Map.of("generation", 1);
                                },
                                fixture.target::set
                        )));
                builderStarted.await();
                Future<?> health = workers.submit(() -> {
                    healthAttempted.countDown();
                    fixture.gate.requireHealthy();
                    healthReturned.countDown();
                });
                healthAttempted.await();
                assertFalse(healthReturned.await(200, TimeUnit.MILLISECONDS),
                        "health check returned while cold publication was still BUILDING");
                releaseBuilder.countDown();
                tooltip.get();
                health.get();
                assertTrue(healthReturned.await(1, TimeUnit.SECONDS));
                fixture.gate.requireHealthy();
            } finally {
                releaseBuilder.countDown();
                workers.shutdownNow();
            }
        });
    }

    @Test
    void reloadFailureIsTerminalAndRetainsTheExactCause() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();
        RuntimeException marker = new RuntimeException("loadFromDirectory failed");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> fixture.gate.runReload(fixture.target::get, () -> {
                    fixture.target.set(null);
                    throw marker;
                }));
        assertSame(marker, thrown);
        IllegalStateException health = assertThrows(
                IllegalStateException.class, fixture.gate::requireHealthy);
        assertSame(marker, health.getCause());
        IllegalStateException tooltip = assertThrows(
                IllegalStateException.class,
                () -> fixture.gate.withTooltipReadLease(() -> {
                }));
        assertSame(marker, tooltip.getCause());
    }

    @Test
    void externalNullResetOfPublishedGenerationFailsClosed() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();
        fixture.withTooltip(() -> fixture.gate.ensureInitialized(
                fixture.target::get,
                () -> Map.of("generation", 1),
                fixture.target::set
        ));
        fixture.target.set(null);

        assertThrows(IllegalStateException.class, () -> fixture.withTooltip(
                () -> fixture.gate.ensureInitialized(
                        fixture.target::get,
                        () -> Map.of("generation", 2),
                        fixture.target::set
                )));
        assertThrows(IllegalStateException.class, fixture.gate::requireHealthy);
    }

    @Test
    void targetReaderExceptionIsRecordedAsATerminalSeamFailure() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();
        RuntimeException marker = new RuntimeException("unexpected target map type");

        IllegalStateException publication = assertThrows(
                IllegalStateException.class,
                () -> fixture.withTooltip(() -> fixture.gate.ensureInitialized(
                        () -> {
                            throw marker;
                        },
                        () -> Map.of("generation", 1),
                        fixture.target::set
                )));
        assertSame(marker, publication.getCause());
        IllegalStateException health = assertThrows(
                IllegalStateException.class, fixture.gate::requireHealthy);
        assertSame(marker, health.getCause());
    }

    @Test
    void swallowedSameThreadBuilderRecursionStillPoisonsTheLifecycle() {
        Fixture fixture = new Fixture();
        fixture.initialReloadAndArm();

        assertThrows(IllegalStateException.class, () -> fixture.withTooltip(
                () -> fixture.gate.ensureInitialized(
                        fixture.target::get,
                        () -> {
                            assertThrows(IllegalStateException.class,
                                    () -> fixture.gate.ensureInitialized(
                                            fixture.target::get,
                                            () -> Map.of("recursive", 1),
                                            fixture.target::set
                                    ));
                            return Map.of("outer", 1);
                        },
                        fixture.target::set
                )));
        assertThrows(IllegalStateException.class, fixture.gate::requireHealthy);
    }

    @Test
    void readToWriteUpgradeFailsWithoutDeadlock() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Fixture fixture = new Fixture();
            fixture.initialReloadAndArm();
            assertThrows(IllegalStateException.class,
                    () -> fixture.gate.withTooltipReadLease(
                            () -> fixture.gate.runReload(
                                    fixture.target::get,
                                    () -> fixture.target.set(null)
                            )));
            assertThrows(IllegalStateException.class, fixture.gate::requireHealthy);
        });
    }

    private static final class Fixture {
        private final ReloadCoordinatedPublication<Map<String, Integer>> gate =
                new ReloadCoordinatedPublication<>();
        private final AtomicReference<Map<String, Integer>> target = new AtomicReference<>();

        private void initialReloadAndArm() {
            gate.runReload(target::get, () -> target.set(null));
            gate.arm();
        }

        private <R> R withTooltip(java.util.function.Supplier<R> callback) {
            AtomicReference<R> result = new AtomicReference<>();
            gate.withTooltipReadLease(() -> result.set(callback.get()));
            return result.get();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test worker was interrupted", exception);
        }
    }
}
