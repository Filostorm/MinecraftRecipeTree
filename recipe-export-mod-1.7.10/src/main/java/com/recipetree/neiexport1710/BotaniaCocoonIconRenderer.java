package com.recipetree.neiexport1710;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Exact correction for Botania 1.12.28-GTNH's Cocoon of Caprice inventory renderer.
 *
 * <p>The owner block renderer creates a fresh {@code TileCocoon} at tick zero and asks its TESR
 * to render with partialTicks zero. The pinned TESR evaluates {@code sin(0) * log(0)}, producing
 * NaN before its model draw. A synthetic tick of one preserves the intended static angle because
 * {@code log(1) == 0}, while keeping the owner's model, texture, transforms, and render path.</p>
 */
final class BotaniaCocoonIconRenderer {
    static final String CONTRACT =
            "botania-cocoon-owner-tesr-finite-inventory-time-v1";
    static final String TILE_CLASS =
            "vazkii.botania.common.block.tile.TileCocoon";
    static final String OWNER_RENDERER_CLASS =
            "vazkii.botania.client.render.tile.RenderTileCocoon";
    static final String TIME_FIELD = "timePassed";
    static final int CORRECTED_SYNTHETIC_TIME = 1;

    private final Class<?> tileClass;
    private final Map<Class<?>, TileEntitySpecialRenderer> rendererMap;
    private final TileEntitySpecialRenderer ownerRenderer;
    private final FiniteSyntheticTimeRenderer adapterRenderer;
    private final Thread renderThread;
    private final boolean requireMinecraftClientThread;
    private boolean leaseActive;

    BotaniaCocoonIconRenderer(
            Class<?> tileClass,
            Map<Class<?>, TileEntitySpecialRenderer> rendererMap,
            TileEntitySpecialRenderer ownerRenderer,
            FiniteSyntheticTimeRenderer adapterRenderer) {
        this(tileClass, rendererMap, ownerRenderer, adapterRenderer, false);
    }

    private BotaniaCocoonIconRenderer(
            Class<?> tileClass,
            Map<Class<?>, TileEntitySpecialRenderer> rendererMap,
            TileEntitySpecialRenderer ownerRenderer,
            FiniteSyntheticTimeRenderer adapterRenderer,
            boolean requireMinecraftClientThread) {
        this.tileClass = tileClass;
        this.rendererMap = rendererMap;
        this.ownerRenderer = ownerRenderer;
        this.adapterRenderer = adapterRenderer;
        this.renderThread = Thread.currentThread();
        this.requireMinecraftClientThread = requireMinecraftClientThread;
    }

    static BotaniaCocoonIconRenderer create() throws Exception {
        if (!Float.isNaN(pinnedOwnerWobbleDegrees(0, 0.0F))) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon tick-zero failure predicate drifted");
        }
        if (Float.floatToIntBits(pinnedOwnerWobbleDegrees(
                CORRECTED_SYNTHETIC_TIME, 0.0F)) != Float.floatToIntBits(0.0F)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon finite-time correction is not static");
        }

        Class<?> tileClass = Class.forName(TILE_CLASS);
        if (!TileEntity.class.isAssignableFrom(tileClass)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon tile is not a TileEntity: "
                            + tileClass.getName());
        }
        Field timeField = tileClass.getField(TIME_FIELD);
        if (timeField.getType() != Integer.TYPE) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon time field is not int");
        }
        Object syntheticTile = tileClass.newInstance();
        if (timeField.getInt(syntheticTile) != 0) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: fresh pinned Botania cocoon tile is not at tick zero");
        }

        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        if (dispatcher == null || dispatcher.mapSpecialRenderers == null) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: TileEntity renderer dispatcher is unavailable");
        }
        TileEntitySpecialRenderer ownerRenderer =
                dispatcher.getSpecialRendererByClass(tileClass);
        String observedRenderer = ownerRenderer == null
                ? "<null>" : ownerRenderer.getClass().getName();
        if (!OWNER_RENDERER_CLASS.equals(observedRenderer)) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon TESR mismatch; expected "
                            + OWNER_RENDERER_CLASS + ", got " + observedRenderer);
        }
        if (dispatcher.mapSpecialRenderers.get(tileClass) != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: pinned Botania cocoon TESR map is not an exact binding");
        }

        @SuppressWarnings("unchecked")
        Map<Class<?>, TileEntitySpecialRenderer> rendererMap =
                (Map<Class<?>, TileEntitySpecialRenderer>) (Map<?, ?>)
                        dispatcher.mapSpecialRenderers;

        FiniteSyntheticTimeRenderer adapterRenderer =
                new FiniteSyntheticTimeRenderer(tileClass, timeField, ownerRenderer);
        adapterRenderer.func_147497_a(dispatcher);
        return new BotaniaCocoonIconRenderer(
                tileClass, rendererMap, ownerRenderer, adapterRenderer, true);
    }

    void drawExactlyOnce(OffscreenRenderer.DrawCall ownerInventoryDraw) throws Exception {
        long invocations = drawAndCount(ownerInventoryDraw);
        if (invocations != 1L) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon owner inventory renderer invoked the "
                            + "finite-time TESR adapter " + invocations
                            + " times instead of exactly once");
        }
    }

    synchronized long drawAndCount(OffscreenRenderer.DrawCall ownerInventoryDraw)
            throws Exception {
        if (ownerInventoryDraw == null) {
            throw new IllegalArgumentException(
                    "ITEM_ICON_RENDER: Botania cocoon owner draw is required");
        }
        if (Thread.currentThread() != renderThread) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon TESR lease left its pinned client thread");
        }
        if (requireMinecraftClientThread
                && (Minecraft.getMinecraft() == null
                || !Minecraft.getMinecraft().func_152345_ab())) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon TESR lease is not on Minecraft's client thread");
        }
        if (leaseActive) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: nested Botania cocoon TESR leases are forbidden");
        }
        if (rendererMap.get(tileClass) != ownerRenderer) {
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon owner TESR binding drifted before draw");
        }
        long attemptsBefore = adapterRenderer.attempts;
        long successesBefore = adapterRenderer.successes;
        long failuresBefore = adapterRenderer.failures;
        leaseActive = true;
        TileEntitySpecialRenderer displaced = rendererMap.put(tileClass, adapterRenderer);
        if (displaced != ownerRenderer) {
            restoreUnexpectedBinding(displaced);
            leaseActive = false;
            throw new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon TESR lease displaced an unexpected renderer");
        }

        Throwable failure = null;
        try {
            ownerInventoryDraw.draw();
        } catch (Throwable error) {
            failure = error;
        } finally {
            TileEntitySpecialRenderer current = rendererMap.get(tileClass);
            if (current != adapterRenderer) {
                failure = merge(failure, new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania cocoon TESR lease changed during draw"));
            } else {
                TileEntitySpecialRenderer removed =
                        rendererMap.put(tileClass, ownerRenderer);
                if (removed != adapterRenderer) {
                    restoreUnexpectedBinding(removed);
                    failure = merge(failure, new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania cocoon owner TESR restore was not exact"));
                }
            }
            leaseActive = false;
        }
        long attempts = adapterRenderer.attempts - attemptsBefore;
        long successes = adapterRenderer.successes - successesBefore;
        long failures = adapterRenderer.failures - failuresBefore;
        if (attempts < 0L || successes < 0L || failures < 0L
                || attempts != successes + failures) {
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon TESR adapter telemetry drifted; attempts="
                            + attempts + ", successes=" + successes
                            + ", failures=" + failures));
        }
        if (failures != 0L) {
            FatalErrors.rethrowIfFatal(adapterRenderer.lastFailure);
            failure = merge(failure, new IllegalStateException(
                    "ITEM_ICON_RENDER: Botania cocoon TESR adapter failed " + failures
                            + " time(s) inside an owner render path that may swallow exceptions",
                    adapterRenderer.lastFailure));
        }
        rethrow(failure);
        return successes;
    }

    private void restoreUnexpectedBinding(TileEntitySpecialRenderer renderer) {
        if (renderer == null) {
            rendererMap.remove(tileClass);
        } else {
            rendererMap.put(tileClass, renderer);
        }
    }

    static float pinnedOwnerWobbleDegrees(int timePassed, float partialTicks) {
        float period = 60.0F - timePassed / 2400.0F * 30.0F;
        float angle = 0.0F;
        if (timePassed % period < 10.0F) {
            float phase = (timePassed + partialTicks) % period;
            float radians = phase / 5.0F * (float) Math.PI * 2.0F;
            angle = (float) Math.sin(radians)
                    * (float) Math.log(timePassed + partialTicks);
        }
        return angle;
    }

    private static Throwable merge(Throwable primary, Throwable additional) {
        if (primary == null) {
            return additional;
        }
        if (additional != primary) {
            primary.addSuppressed(additional);
        }
        return primary;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure == null) {
            return;
        }
        FatalErrors.rethrowIfFatal(failure);
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        throw new IllegalStateException(
                "ITEM_ICON_RENDER: Botania cocoon TESR adapter failed", failure);
    }

    static final class FiniteSyntheticTimeRenderer extends TileEntitySpecialRenderer {
        private final Class<?> tileClass;
        private final Field timeField;
        private final TileEntitySpecialRenderer ownerRenderer;
        private final Thread renderThread;
        long attempts;
        long successes;
        long failures;
        Throwable lastFailure;

        FiniteSyntheticTimeRenderer(
                Class<?> tileClass, Field timeField,
                TileEntitySpecialRenderer ownerRenderer) {
            this.tileClass = tileClass;
            this.timeField = timeField;
            this.ownerRenderer = ownerRenderer;
            this.renderThread = Thread.currentThread();
        }

        @Override
        public void renderTileEntityAt(
                TileEntity tile, double x, double y, double z, float partialTicks) {
            attempts++;
            int originalTime = 0;
            boolean timeRead = false;
            boolean timeChanged = false;
            Throwable failure = null;
            try {
                if (Thread.currentThread() != renderThread) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania cocoon TESR adapter left its pinned client thread");
                }
                if (tile == null || tile.getClass() != tileClass) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania cocoon TESR adapter received the wrong tile");
                }
                if (Float.floatToIntBits(partialTicks) != Float.floatToIntBits(0.0F)) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania cocoon inventory partialTicks drifted from zero");
                }
                originalTime = timeField.getInt(tile);
                timeRead = true;
                if (originalTime != 0) {
                    throw new IllegalStateException(
                            "ITEM_ICON_RENDER: Botania cocoon owner renderer did not create a "
                                    + "tick-zero inventory tile; got " + originalTime);
                }
                timeField.setInt(tile, CORRECTED_SYNTHETIC_TIME);
                timeChanged = true;
                ownerRenderer.renderTileEntityAt(tile, x, y, z, partialTicks);
            } catch (Throwable error) {
                failure = error;
            } finally {
                if (timeRead && timeChanged) {
                    try {
                        timeField.setInt(tile, originalTime);
                    } catch (Throwable restore) {
                        failure = merge(failure, restore);
                    }
                }
            }
            if (failure != null) {
                failures++;
                lastFailure = failure;
                FatalErrors.rethrowIfFatal(failure);
                throw new IllegalStateException(
                        "ITEM_ICON_RENDER: Botania cocoon owner TESR failed", failure);
            }
            successes++;
        }
    }
}
