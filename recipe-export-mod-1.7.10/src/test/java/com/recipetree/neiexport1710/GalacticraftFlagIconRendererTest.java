package com.recipetree.neiexport1710;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.junit.Test;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GalacticraftFlagIconRendererTest {
    @Test
    public void deterministicFrameCannotEnterOwnerRefreshBranch() {
        assertTrue(GalacticraftFlagIconRenderer.pinnedOwnerRefreshPredicate(0L));
        assertTrue(GalacticraftFlagIconRenderer.pinnedOwnerRefreshPredicate(100L));
        assertFalse(GalacticraftFlagIconRenderer.pinnedOwnerRefreshPredicate(
                GalacticraftFlagIconRenderer.DETERMINISTIC_TOTAL_TIME));
        assertEquals(1L, GalacticraftFlagIconRenderer.DETERMINISTIC_TOTAL_TIME);
        assertEquals(0, GalacticraftFlagIconRenderer.DETERMINISTIC_ENTITY_ID);
        assertEquals(48, GalacticraftFlagIconRenderer.CANONICAL_FLAG_WIDTH);
        assertEquals(32, GalacticraftFlagIconRenderer.CANONICAL_FLAG_HEIGHT);
        assertEquals(127, GalacticraftFlagIconRenderer.CANONICAL_COLOR_BYTE);
    }

    @Test
    public void registeredRendererLeaseCountsOwnerAndRestoresExactBinding() throws Exception {
        final Item item = new Item();
        final ItemStack stack = new ItemStack(item, 1, 0);
        final RecordingRenderer owner = new RecordingRenderer();
        final MapBinding binding = new MapBinding();
        binding.set(item, owner);
        GalacticraftFlagIconRenderer.CountingItemRenderer adapter =
                new GalacticraftFlagIconRenderer.CountingItemRenderer(
                        owner, directInvocation());
        GalacticraftFlagIconRenderer lease = new GalacticraftFlagIconRenderer(
                item, binding, owner, adapter);

        long successes = lease.drawAndCount(new OffscreenRenderer.DrawCall() {
            @Override
            public void draw() {
                binding.get(item).renderItem(
                        IItemRenderer.ItemRenderType.INVENTORY, stack);
            }
        });

        assertEquals(1L, successes);
        assertEquals(1, owner.calls);
        assertSame(owner, binding.get(item));
    }

    @Test
    public void swallowedOwnerFailureEscapesAfterExactBindingRestore() throws Exception {
        final Item item = new Item();
        final ItemStack stack = new ItemStack(item, 1, 0);
        final RecordingRenderer owner = new RecordingRenderer();
        owner.failure = new IllegalStateException("synthetic owner failure");
        final MapBinding binding = new MapBinding();
        binding.set(item, owner);
        GalacticraftFlagIconRenderer.CountingItemRenderer adapter =
                new GalacticraftFlagIconRenderer.CountingItemRenderer(
                        owner, directInvocation());
        GalacticraftFlagIconRenderer lease = new GalacticraftFlagIconRenderer(
                item, binding, owner, adapter);

        try {
            lease.drawAndCount(new OffscreenRenderer.DrawCall() {
                @Override
                public void draw() {
                    try {
                        binding.get(item).renderItem(
                                IItemRenderer.ItemRenderType.INVENTORY, stack);
                    } catch (RuntimeException swallowedByNei) {
                        // Mirrors NEI's safe item-render context.
                    }
                }
            });
            fail("Expected swallowed owner failure to escape the lease");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("may swallow exceptions"));
        }
        assertSame(owner, binding.get(item));
    }

    @Test
    public void genericStateLeaseRestoresAndVerifiesAfterInvocationFailure() {
        final StringBuilder order = new StringBuilder();
        try {
            GalacticraftFlagIconRenderer.runRestoring(
                    new GalacticraftFlagIconRenderer.ScopedMutation() {
                @Override
                public void install() {
                    order.append('i');
                }

                @Override
                public void invoke() {
                    order.append('d');
                    throw new IllegalStateException("owner failed");
                }

                @Override
                public void restore() {
                    order.append('r');
                }

                @Override
                public void verifyRestored() {
                    order.append('v');
                }
            });
            fail("Expected owner failure");
        } catch (Throwable expected) {
            assertTrue(expected.getMessage().contains("owner failed"));
        }
        assertEquals("idrv", order.toString());
    }

    @Test
    public void unchangedMatrixStateNeedsNoRepair() throws Throwable {
        FakeMatrixState matrix = new FakeMatrixState(GL11.GL_MODELVIEW, 4);
        int snapshot = GalacticraftFlagIconRenderer.requireModelViewMatrixState(matrix);

        GalacticraftFlagIconRenderer.restoreModelViewMatrixState(matrix, snapshot);

        assertEquals(4, matrix.depth);
        assertEquals(0, matrix.modeWrites);
        assertEquals(0, matrix.pops);
    }

    @Test
    public void excessModelViewFramesAreRepairedAndStillFailClosed() {
        FakeMatrixState matrix = new FakeMatrixState(GL11.GL_MODELVIEW, 6);
        try {
            GalacticraftFlagIconRenderer.restoreModelViewMatrixState(matrix, 4);
            fail("Expected leaked matrix frames to remain a hard failure");
        } catch (Throwable expected) {
            assertTrue(expected.getMessage().contains("leaked 2 modelview matrix frame"));
        }
        assertEquals(GL11.GL_MODELVIEW, matrix.mode);
        assertEquals(4, matrix.depth);
        assertEquals(2, matrix.pops);
    }

    @Test
    public void modelViewUnderflowAndWrongModeBothFailClosed() {
        FakeMatrixState underflow = new FakeMatrixState(GL11.GL_MODELVIEW, 3);
        try {
            GalacticraftFlagIconRenderer.restoreModelViewMatrixState(underflow, 4);
            fail("Expected modelview underflow failure");
        } catch (Throwable expected) {
            assertTrue(expected.getMessage().contains("underflowed"));
        }
        assertEquals(3, underflow.depth);
        assertEquals(0, underflow.pops);

        FakeMatrixState wrongMode = new FakeMatrixState(GL11.GL_PROJECTION, 4);
        try {
            GalacticraftFlagIconRenderer.restoreModelViewMatrixState(wrongMode, 4);
            fail("Expected wrong matrix mode failure");
        } catch (Throwable expected) {
            assertTrue(expected.getMessage().contains("changed matrix mode"));
        }
        assertEquals(GL11.GL_MODELVIEW, wrongMode.mode);
        assertEquals(1, wrongMode.modeWrites);
    }

    @Test
    public void unchangedLedgersAreNotClearedReaddedOrRebound() throws Throwable {
        TrackingSet races = new TrackingSet();
        races.add("race-a");
        TrackingList requests = new TrackingList();
        requests.add("request-a");
        races.resetMutationCounts();
        requests.resetMutationCounts();
        FakeLedgerAccess access = new FakeLedgerAccess(races, requests);
        GalacticraftFlagIconRenderer.LedgerSnapshot snapshot =
                GalacticraftFlagIconRenderer.snapshotLedgers(access);

        GalacticraftFlagIconRenderer.restoreLedgers(access, snapshot);

        assertSame(races, access.races);
        assertSame(requests, access.requests);
        assertEquals(0, races.clears);
        assertEquals(0, races.addAlls);
        assertEquals(0, requests.clears);
        assertEquals(0, requests.addAlls);
        assertEquals(0, access.raceFieldWrites);
        assertEquals(0, access.requestFieldWrites);
    }

    @Test
    public void ledgerContentAndRequestReferenceMutationRepairsThenFailsClosed()
            throws Throwable {
        TrackingSet races = new TrackingSet();
        races.add("race-a");
        TrackingList requests = new TrackingList();
        requests.add("request-a");
        FakeLedgerAccess access = new FakeLedgerAccess(races, requests);
        GalacticraftFlagIconRenderer.LedgerSnapshot snapshot =
                GalacticraftFlagIconRenderer.snapshotLedgers(access);

        races.clear();
        races.add("mutated-race");
        requests.clear();
        requests.add("mutated-original-list");
        access.requests = new ArrayList<Object>(Arrays.<Object>asList("replacement-list"));
        try {
            GalacticraftFlagIconRenderer.restoreLedgers(access, snapshot);
            fail("Expected repaired ledger mutation to remain a hard failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("state repaired before abort"));
        }

        assertSame(races, access.races);
        assertEquals(new HashSet<Object>(Arrays.<Object>asList("race-a")), races);
        assertSame(requests, access.requests);
        assertEquals(Arrays.<Object>asList("request-a"), requests);
        assertEquals(0, access.raceFieldWrites);
        assertEquals(1, access.requestFieldWrites);
        GalacticraftFlagIconRenderer.verifyLedgers(access, snapshot);
    }

    @Test
    public void rejectedFinalSpaceRaceReferenceRepairIsSurfaced() throws Throwable {
        TrackingSet races = new TrackingSet();
        races.add("race-a");
        TrackingList requests = new TrackingList();
        FakeLedgerAccess access = new FakeLedgerAccess(races, requests);
        GalacticraftFlagIconRenderer.LedgerSnapshot snapshot =
                GalacticraftFlagIconRenderer.snapshotLedgers(access);
        access.races = new HashSet<Object>(Arrays.<Object>asList("replacement"));
        access.rejectRaceFieldWrite = true;

        try {
            GalacticraftFlagIconRenderer.restoreLedgers(access, snapshot);
            fail("Expected final-field replacement repair failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("protected client ledgers"));
            assertTrue(containsSuppressedMessage(expected, "static final field replacement"));
        }
        assertEquals(1, access.raceFieldWrites);
    }

    private static boolean containsSuppressedMessage(Throwable error, String text) {
        for (Throwable suppressed : error.getSuppressed()) {
            if (suppressed.getMessage() != null
                    && suppressed.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static GalacticraftFlagIconRenderer.OwnerInvocation directInvocation() {
        return new GalacticraftFlagIconRenderer.OwnerInvocation() {
            @Override
            public void invoke(
                    IItemRenderer owner,
                    IItemRenderer.ItemRenderType type,
                    ItemStack stack,
                    Object[] data) {
                owner.renderItem(type, stack, data);
            }
        };
    }

    private static final class MapBinding
            implements GalacticraftFlagIconRenderer.RendererBinding {
        private final Map<Item, IItemRenderer> values =
                new IdentityHashMap<Item, IItemRenderer>();

        @Override
        public IItemRenderer get(Item item) {
            return values.get(item);
        }

        @Override
        public void set(Item item, IItemRenderer renderer) {
            values.put(item, renderer);
        }
    }

    private static final class RecordingRenderer implements IItemRenderer {
        int calls;
        RuntimeException failure;

        @Override
        public boolean handleRenderType(ItemStack item, ItemRenderType type) {
            return true;
        }

        @Override
        public boolean shouldUseRenderHelper(
                ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
            return true;
        }

        @Override
        public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
            calls++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class FakeMatrixState
            implements GalacticraftFlagIconRenderer.MatrixStateAccess {
        int mode;
        int depth;
        int modeWrites;
        int pops;

        private FakeMatrixState(int mode, int depth) {
            this.mode = mode;
            this.depth = depth;
        }

        @Override
        public int matrixMode() {
            return mode;
        }

        @Override
        public int modelViewDepth() {
            return depth;
        }

        @Override
        public void matrixMode(int mode) {
            this.mode = mode;
            modeWrites++;
        }

        @Override
        public void popMatrix() {
            depth--;
            pops++;
        }
    }

    private static final class FakeLedgerAccess
            implements GalacticraftFlagIconRenderer.LedgerAccess {
        Object races;
        Object requests;
        int raceFieldWrites;
        int requestFieldWrites;
        boolean rejectRaceFieldWrite;

        private FakeLedgerAccess(Object races, Object requests) {
            this.races = races;
            this.requests = requests;
        }

        @Override
        public Object spaceRaceSet() {
            return races;
        }

        @Override
        public void spaceRaceSet(Object value) {
            raceFieldWrites++;
            if (rejectRaceFieldWrite) {
                throw new IllegalAccessError("static final field replacement rejected");
            }
            races = value;
        }

        @Override
        public Object flagRequestList() {
            return requests;
        }

        @Override
        public void flagRequestList(Object value) {
            requestFieldWrites++;
            requests = value;
        }
    }

    private static final class TrackingSet extends HashSet<Object> {
        int clears;
        int addAlls;

        @Override
        public void clear() {
            clears++;
            super.clear();
        }

        @Override
        public boolean addAll(java.util.Collection<?> values) {
            addAlls++;
            return super.addAll(values);
        }

        void resetMutationCounts() {
            clears = 0;
            addAlls = 0;
        }
    }

    private static final class TrackingList extends ArrayList<Object> {
        int clears;
        int addAlls;

        @Override
        public void clear() {
            clears++;
            super.clear();
        }

        @Override
        public boolean addAll(java.util.Collection<?> values) {
            addAlls++;
            return super.addAll(values);
        }

        void resetMutationCounts() {
            clears = 0;
            addAlls = 0;
        }
    }
}
