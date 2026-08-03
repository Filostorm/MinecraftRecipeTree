package com.recipetree.neiexport1710;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BotaniaCocoonIconRendererTest {
    @Test
    public void pinnedOwnerMathProvesTickZeroNanAndTickOneStaticFrame() {
        assertTrue(Float.isNaN(
                BotaniaCocoonIconRenderer.pinnedOwnerWobbleDegrees(0, 0.0F)));
        assertEquals(0.0F, BotaniaCocoonIconRenderer.pinnedOwnerWobbleDegrees(
                BotaniaCocoonIconRenderer.CORRECTED_SYNTHETIC_TIME, 0.0F), 0.0F);
    }

    @Test
    public void adapterExposesTickOneOnlyDuringOwnerTesrCall() throws Exception {
        FakeCocoonTile tile = new FakeCocoonTile();
        RecordingRenderer owner = new RecordingRenderer();
        BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer adapter =
                adapter(owner);

        adapter.renderTileEntityAt(tile, 1.0D, 2.0D, 3.0D, 0.0F);

        assertEquals(1, owner.observedTime);
        assertEquals(0, tile.timePassed);
        assertEquals(1L, adapter.attempts);
        assertEquals(1L, adapter.successes);
        assertEquals(0L, adapter.failures);
    }

    @Test
    public void adapterRestoresTickZeroWhenOwnerTesrFails() throws Exception {
        FakeCocoonTile tile = new FakeCocoonTile();
        RecordingRenderer owner = new RecordingRenderer();
        owner.failure = new IllegalStateException("synthetic owner failure");
        BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer adapter =
                adapter(owner);

        try {
            adapter.renderTileEntityAt(tile, 0.0D, 0.0D, 0.0D, 0.0F);
            fail("Expected owner TESR failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("owner TESR failed"));
        }
        assertEquals(1, owner.observedTime);
        assertEquals(0, tile.timePassed);
        assertEquals(1L, adapter.attempts);
        assertEquals(0L, adapter.successes);
        assertEquals(1L, adapter.failures);
    }

    @Test(expected = IllegalStateException.class)
    public void adapterRejectsNonzeroInventoryPartialTicks() throws Exception {
        adapter(new RecordingRenderer()).renderTileEntityAt(
                new FakeCocoonTile(), 0.0D, 0.0D, 0.0D, 0.5F);
    }

    @Test
    public void leaseCountsSuccessAndRestoresExactOwnerBinding() throws Exception {
        final FakeCocoonTile tile = new FakeCocoonTile();
        RecordingRenderer owner = new RecordingRenderer();
        final BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer adapter = adapter(owner);
        final Map<Class<?>, TileEntitySpecialRenderer> renderers =
                new HashMap<Class<?>, TileEntitySpecialRenderer>();
        renderers.put(FakeCocoonTile.class, owner);
        BotaniaCocoonIconRenderer lease = new BotaniaCocoonIconRenderer(
                FakeCocoonTile.class, renderers, owner, adapter);

        long successes = lease.drawAndCount(new OffscreenRenderer.DrawCall() {
            @Override
            public void draw() {
                renderers.get(FakeCocoonTile.class).renderTileEntityAt(
                        tile, 0.0D, 0.0D, 0.0D, 0.0F);
            }
        });

        assertEquals(1L, successes);
        assertSame(owner, renderers.get(FakeCocoonTile.class));
        assertEquals(0, tile.timePassed);
    }

    @Test
    public void leaseRethrowsSwallowedAdapterFailureAfterRestoringOwnerBinding()
            throws Exception {
        final FakeCocoonTile tile = new FakeCocoonTile();
        RecordingRenderer owner = new RecordingRenderer();
        owner.failure = new IllegalStateException("synthetic owner failure");
        final BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer adapter = adapter(owner);
        final Map<Class<?>, TileEntitySpecialRenderer> renderers =
                new HashMap<Class<?>, TileEntitySpecialRenderer>();
        renderers.put(FakeCocoonTile.class, owner);
        BotaniaCocoonIconRenderer lease = new BotaniaCocoonIconRenderer(
                FakeCocoonTile.class, renderers, owner, adapter);

        try {
            lease.drawAndCount(new OffscreenRenderer.DrawCall() {
                @Override
                public void draw() {
                    try {
                        renderers.get(FakeCocoonTile.class).renderTileEntityAt(
                                tile, 0.0D, 0.0D, 0.0D, 0.0F);
                    } catch (RuntimeException swallowedByNei) {
                        // Mirrors NEI safeItemRenderContext's exception substitution.
                    }
                }
            });
            fail("Expected swallowed adapter failure to escape the lease");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("may swallow exceptions"));
        }
        assertSame(owner, renderers.get(FakeCocoonTile.class));
        assertEquals(0, tile.timePassed);
    }

    private static BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer adapter(
            TileEntitySpecialRenderer owner) throws Exception {
        Field timeField = FakeCocoonTile.class.getField("timePassed");
        return new BotaniaCocoonIconRenderer.FiniteSyntheticTimeRenderer(
                FakeCocoonTile.class, timeField, owner);
    }

    public static final class FakeCocoonTile extends TileEntity {
        public int timePassed;
    }

    private static final class RecordingRenderer extends TileEntitySpecialRenderer {
        int observedTime = -1;
        RuntimeException failure;

        @Override
        public void renderTileEntityAt(
                TileEntity tile, double x, double y, double z, float partialTicks) {
            observedTime = ((FakeCocoonTile) tile).timePassed;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
