package com.recipetree.reiexport118.compat;

import com.recipetree.reiexport118.ReiExportMod;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.latvian.mods.kubejs.item.ItemTooltipEventJS;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Serializes KubeJS's lazy tooltip-handler build while preserving REI's parallel cache. */
public final class KubeJsTooltipPublicationRepair {
    private static final ReloadCoordinatedPublication<HandlerMap> HANDLER_PUBLICATION =
            new ReloadCoordinatedPublication<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private KubeJsTooltipPublicationRepair() {
    }

    static void arm() {
        try {
            HANDLER_PUBLICATION.arm();
        } catch (RuntimeException | Error exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Arming the KubeJS tooltip/reload lifecycle failed explicitly",
                    exception
            );
            throw exception;
        }
    }

    public static void invokeTooltip(
            ClientTooltipEvent.Item delegate,
            ItemStack stack,
            List<Component> text,
            TooltipFlag flag
    ) {
        try {
            HANDLER_PUBLICATION.beginTooltipReadLease();
        } catch (RuntimeException | Error exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Acquiring the KubeJS item-tooltip read lease failed explicitly",
                    exception
            );
            throw exception;
        }
        try {
            delegate.append(stack, text, flag);
        } catch (RuntimeException | Error exception) {
            HANDLER_PUBLICATION.recordTooltipFailure(exception);
            ReiExportMod.LOGGER.error(
                    "[reiexport] KubeJS item-tooltip callback failed; lifecycle is terminal",
                    exception
            );
            throw exception;
        } finally {
            HANDLER_PUBLICATION.endTooltipReadLease();
        }
    }

    public static void requireHealthy() {
        try {
            HANDLER_PUBLICATION.requireHealthy();
        } catch (RuntimeException | Error exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] KubeJS tooltip/reload lifecycle health check failed explicitly",
                    exception
            );
            throw exception;
        }
    }

    /**
     * Returns an identity-bearing snapshot only after the current KubeJS generation has been
     * completely built and published. The MM2 REI lifecycle gate compares this across START and
     * END so a concurrent or hidden client-script reload cannot be accepted silently.
     */
    public static PublishedSnapshot requirePublishedSnapshot() {
        requireHealthy();
        HandlerMap handlers = asExactMapOrNull(
                dev.latvian.mods.kubejs.client.KubeJSClientEventHandler.staticItemTooltips);
        int generation = GENERATION.get();
        if (handlers == null || handlers.isEmpty() || generation <= 0) {
            throw new IllegalStateException(
                    "KubeJS item.tooltip handlers are not completely published: generation="
                            + generation + ", handlers="
                            + (handlers == null ? "null" : handlers.size()));
        }
        return new PublishedSnapshot(generation, handlers);
    }

    public static void runReload(Runnable reloadBody) {
        try {
            HANDLER_PUBLICATION.runReload(
                    () -> asExactMapOrNull(
                            dev.latvian.mods.kubejs.client.KubeJSClientEventHandler
                                    .staticItemTooltips
                    ),
                    reloadBody
            );
        } catch (RuntimeException | Error exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] KubeJS client-script reload lifecycle failed explicitly",
                    exception
            );
            throw exception;
        }
    }

    public static void ensureInitialized(
            Supplier<HandlerMap> currentReader,
            Consumer<HandlerMap> publisher
    ) {
        try {
            HANDLER_PUBLICATION.ensureInitialized(
                    currentReader,
                    KubeJsTooltipPublicationRepair::buildCompleteHandlerMap,
                    publisher
            );
        } catch (RuntimeException | LinkageError exception) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Exact KubeJS item.tooltip publication failed explicitly",
                    exception
            );
            throw exception;
        }
    }

    public static HandlerMap asExactMapOrNull(
            Map<Item, List<ItemTooltipEventJS.StaticTooltipHandler>> handlers
    ) {
        if (handlers == null) {
            return null;
        }
        if (handlers instanceof HandlerMap exact) {
            return exact;
        }
        throw new IllegalStateException(
                "KubeJS staticItemTooltips was populated outside the exact publication lifecycle"
        );
    }

    private static HandlerMap buildCompleteHandlerMap() {
        HandlerMap candidate = new HandlerMap();
        ItemTooltipEventJS event = new ItemTooltipEventJS(candidate);
        boolean cancelled = event.post(
                ScriptType.CLIENT,
                KubeJsTooltipConcurrencyContract.TOOLTIP_EVENT_NAME
        );
        if (cancelled) {
            throw new IllegalStateException(
                    "non-cancellable KubeJS item.tooltip registration unexpectedly returned true"
            );
        }

        int handlerCount = 0;
        for (Map.Entry<Item, List<ItemTooltipEventJS.StaticTooltipHandler>> entry
                : candidate.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()
                    || entry.getValue().stream().anyMatch(handler -> handler == null)) {
                throw new IllegalStateException(
                        "KubeJS item.tooltip produced a null or empty handler-map entry"
                );
            }
            handlerCount = Math.addExact(handlerCount, entry.getValue().size());
        }
        ReiExportMod.LOGGER.warn(
                "[reiexport] Completed serialized KubeJS item.tooltip handler build: "
                        + "generation={}, itemKeys={}, handlers={}, thread={}; map publication "
                        + "follows only after all returned entries pass structural validation",
                GENERATION.incrementAndGet(),
                candidate.size(),
                handlerCount,
                Thread.currentThread().getName()
        );
        return candidate;
    }

    /** Preserves KubeJS's exact public field type while giving the gate an identity-bearing type. */
    public static final class HandlerMap
            extends HashMap<Item, List<ItemTooltipEventJS.StaticTooltipHandler>> {
    }

    /** The handler-map identity is significant; Map equality is intentionally not used. */
    public record PublishedSnapshot(int generation, HandlerMap handlers) {
        public PublishedSnapshot {
            if (generation <= 0 || handlers == null || handlers.isEmpty()) {
                throw new IllegalArgumentException("Published KubeJS snapshot is incomplete");
            }
        }

        public boolean isSamePublication(PublishedSnapshot other) {
            return other != null
                    && generation == other.generation
                    && handlers == other.handlers;
        }
    }
}
