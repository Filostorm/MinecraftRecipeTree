package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2ReiLifecycleSequenceTest {
    @Test
    void acceptsAuditedPacketPrecursorAndAuthoritativeNativeAndOwnedReloadSequence() {
        Mm2ReiLifecycleSequence sequence = new Mm2ReiLifecycleSequence();
        Object manager = new Object();
        Object handlers = new Object();
        Thread owner = Thread.currentThread();

        sequence.arm();
        assertTrue(sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                manager,
                new Thread("packet")));
        assertTrue(sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                owner));
        assertTrue(sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                owner));
        sequence.beginOwnedReload(manager, owner);
        sequence.enterReloadStage(Mm2ReiLifecycleSequence.ReloadStage.START, owner);
        sequence.exitReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.START,
                owner,
                new Mm2ReiLifecycleSequence.Publication(2, handlers));
        sequence.enterReloadStage(Mm2ReiLifecycleSequence.ReloadStage.END, owner);
        sequence.exitReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.END,
                owner,
                new Mm2ReiLifecycleSequence.Publication(2, handlers));
        sequence.completeOwnedReload(owner);
        sequence.requireComplete();
        assertEquals(Mm2ReiLifecycleSequence.State.COMPLETE, sequence.state());
    }

    @Test
    void acceptsDirectAuthoritativeRenderSequenceWithoutPacketPrecursor() {
        Mm2ReiLifecycleSequence sequence = ownedSequence();
        assertEquals(Mm2ReiLifecycleSequence.State.OWNED_RELOAD, sequence.state());
    }

    @Test
    void rejectsReorderingDuplicationForeignThreadsAndPublicationDrift() {
        Mm2ReiLifecycleSequence sequence = new Mm2ReiLifecycleSequence();
        sequence.arm();
        assertThrows(IllegalStateException.class, () -> sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                new Object(),
                new Thread("packet")));
        assertEquals(Mm2ReiLifecycleSequence.State.FAILED, sequence.state());

        Mm2ReiLifecycleSequence drift = ownedSequence();
        Object first = new Object();
        drift.enterReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.START, Thread.currentThread());
        drift.exitReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.START,
                Thread.currentThread(),
                new Mm2ReiLifecycleSequence.Publication(1, first));
        drift.enterReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.END, Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> drift.exitReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.END,
                Thread.currentThread(),
                new Mm2ReiLifecycleSequence.Publication(2, first)));
        assertEquals(Mm2ReiLifecycleSequence.State.FAILED, drift.state());

        Mm2ReiLifecycleSequence foreign = ownedSequence();
        assertThrows(IllegalStateException.class, () -> foreign.enterReloadStage(
                Mm2ReiLifecycleSequence.ReloadStage.START, new Thread("foreign")));
        assertEquals(Mm2ReiLifecycleSequence.State.FAILED, foreign.state());
    }

    @Test
    void rejectsManagerIdentityDriftAndIncompleteExport() {
        Mm2ReiLifecycleSequence sequence = new Mm2ReiLifecycleSequence();
        Object first = new Object();
        sequence.arm();
        sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                first,
                Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                new Object(),
                Thread.currentThread()));

        Mm2ReiLifecycleSequence incomplete = new Mm2ReiLifecycleSequence();
        incomplete.arm();
        assertThrows(IllegalStateException.class, incomplete::requireComplete);
        assertEquals(Mm2ReiLifecycleSequence.State.FAILED, incomplete.state());
    }

    @Test
    void rejectsPacketPrecursorDuplicationLatenessAndManagerDrift() {
        Object manager = new Object();
        Thread packet = new Thread("packet");

        Mm2ReiLifecycleSequence duplicate = new Mm2ReiLifecycleSequence();
        duplicate.arm();
        duplicate.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                manager,
                packet);
        assertThrows(IllegalStateException.class, () -> duplicate.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                manager,
                packet));

        Mm2ReiLifecycleSequence late = new Mm2ReiLifecycleSequence();
        late.arm();
        late.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> late.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                manager,
                packet));

        Mm2ReiLifecycleSequence drift = new Mm2ReiLifecycleSequence();
        drift.arm();
        drift.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.PACKET,
                manager,
                packet);
        assertThrows(IllegalStateException.class, () -> drift.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                new Object(),
                Thread.currentThread()));
    }

    @Test
    void rejectsRenderCallbackDuplicationAndThreadDrift() {
        Object manager = new Object();

        Mm2ReiLifecycleSequence duplicateStart = new Mm2ReiLifecycleSequence();
        duplicateStart.arm();
        duplicateStart.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> duplicateStart.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread()));

        Mm2ReiLifecycleSequence foreignEnd = new Mm2ReiLifecycleSequence();
        foreignEnd.arm();
        foreignEnd.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> foreignEnd.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                new Thread("foreign-render")));

        Mm2ReiLifecycleSequence duplicateEnd = new Mm2ReiLifecycleSequence();
        duplicateEnd.arm();
        duplicateEnd.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        duplicateEnd.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        assertThrows(IllegalStateException.class, () -> duplicateEnd.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread()));
    }

    private static Mm2ReiLifecycleSequence ownedSequence() {
        Mm2ReiLifecycleSequence sequence = new Mm2ReiLifecycleSequence();
        Object manager = new Object();
        sequence.arm();
        sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.START,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        sequence.suppressNative(
                Mm2ReiLifecycleSequence.NativeStage.END,
                Mm2ReiLifecycleSequence.NativeThreadRole.RENDER,
                manager,
                Thread.currentThread());
        sequence.beginOwnedReload(manager, Thread.currentThread());
        return sequence;
    }
}
