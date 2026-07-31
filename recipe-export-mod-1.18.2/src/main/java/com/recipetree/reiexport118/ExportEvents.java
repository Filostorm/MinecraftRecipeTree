package com.recipetree.reiexport118;

import com.recipetree.reiexport118.compat.CapsuleRecipeSyncCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ExportEvents {
    private ExportEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipesUpdatedBeforeRei(RecipesUpdatedEvent event) {
        CapsuleRecipeSyncCompatibility.hydrateBeforeSynchronousRei(event.getRecipeManager());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            WorldBootstrap.tick();
        } else {
            WorldBootstrap.observeActiveLevel();
            ExportCoordinator.tick();
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean exactNullBootstrapContext = minecraft.level == null
                && minecraft.player == null
                && minecraft.gameMode == null
                && minecraft.getConnection() == null
                && event.getPlayer() == null
                && event.getMultiPlayerGameMode() == null
                && event.getConnection() == null;
        if (minecraft.isSameThread()
                && exactNullBootstrapContext
                && WorldBootstrap.consumeExpectedBootstrapLogout()) {
            ReiExportMod.LOGGER.info(
                    "[reiexport] Observed the single owned logout event emitted inside the "
                            + "native MM2 load/create world handoff; readiness remains "
                            + "unclaimed and no retry or fallback was invoked");
            return;
        }
        if (!minecraft.isSameThread()) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Client logout arrived off the pinned render thread; it cannot "
                            + "be classified as the native bootstrap handoff and will remain "
                    + "terminal");
        }
        if (!exactNullBootstrapContext && minecraft.level == null) {
            ReiExportMod.LOGGER.error(
                    "[reiexport] Client logout during MM2 world bootstrap carried non-null "
                            + "player/game-mode/connection state; it is not the pinned native "
                            + "title-to-world handoff and will remain terminal");
        }
        minecraft.execute(ExportCoordinator::abortForLogout);
    }
}
