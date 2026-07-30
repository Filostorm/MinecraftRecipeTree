package com.recipetree.jeiexport;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumSet;
import java.util.Locale;

public final class ExportEvents {
    private ExportEvents() {
    }

    /** Ticks spent in a loaded level, for the auto-export delay. */
    private static int ticksInLevel;
    private static boolean autoStartAttempted;
    private static final int AUTO_START_DELAY_TICKS = 100;

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ExportCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ticksInLevel = 0;
            return;
        }
        ticksInLevel++;
        ExportJob job = ExportJob.current();
        if (job != null) {
            job.tick();
        } else {
            maybeAutoStart();
        }
    }

    /**
     * Headless/CI mode: launch with -Djeiexport.auto=all|items|recipes|mobs|blockdrops|trades (and optionally
     * -Djeiexport.iconScale=N) to start an export automatically shortly after world load.
     */
    private static void maybeAutoStart() {
        if (autoStartAttempted || ticksInLevel < AUTO_START_DELAY_TICKS) {
            return;
        }
        String auto = System.getProperty("jeiexport.auto");
        if (auto == null || auto.isEmpty()) {
            autoStartAttempted = true;
            return;
        }
        if (JeiExportPlugin.runtime() == null) {
            return; // JEI not ready yet; keep waiting
        }
        autoStartAttempted = true;
        EnumSet<ExportJob.Phase> phases = switch (auto.toLowerCase(Locale.ROOT)) {
            case "all" -> EnumSet.allOf(ExportJob.Phase.class);
            case "items" -> EnumSet.of(ExportJob.Phase.ITEMS);
            case "recipes" -> EnumSet.of(ExportJob.Phase.ITEMS, ExportJob.Phase.RECIPES);
            case "mobs" -> EnumSet.of(ExportJob.Phase.MOBS);
            case "blockdrops" -> EnumSet.of(ExportJob.Phase.BLOCK_DROPS);
            case "trades" -> EnumSet.of(ExportJob.Phase.ITEMS, ExportJob.Phase.TRADES);
            default -> null;
        };
        if (phases == null) {
            JeiExportMod.LOGGER.error(
                    "[jeiexport] Invalid -Djeiexport.auto={} (expected all, items, recipes, mobs, blockdrops, or trades); no export was started",
                    auto);
            return;
        }
        int iconScale = Integer.getInteger(
                "jeiexport.iconScale", ExportManifestContract.DEFAULT_ICON_SCALE);
        try {
            ExportJob.start(JeiExportPlugin.runtime(), phases, iconScale);
            ExportJob.chat("Auto-export started (" + phases + ", icon scale " + iconScale + ")", ChatFormatting.GREEN);
        } catch (Exception e) {
            JeiExportMod.LOGGER.error("Auto-export failed to start", e);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Framebuffers must be destroyed on the render thread.
        Minecraft.getInstance().execute(ExportJob::abortNow);
    }
}
