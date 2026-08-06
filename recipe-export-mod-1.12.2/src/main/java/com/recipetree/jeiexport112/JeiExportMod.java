package com.recipetree.jeiexport112;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = JeiExportMod.MOD_ID,
        name = "Recipe Tree JEI/HEI Exporter",
        version = JeiExportMod.VERSION,
        clientSideOnly = true,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = JeiExportMod.JEI_DEPENDENCY
)
public final class JeiExportMod {
    static final String MOD_ID = "jeiexport";
    static final String VERSION = "1.1.1";
    static final String JEI_DEPENDENCY = "required-after:jei@[4.12.0.214,5.0.0)";
    static final Logger LOGGER = LogManager.getLogger("jeiexport");
    static final ExportCoordinator COORDINATOR = new ExportCoordinator();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(COORDINATOR);
        ClientCommandHandler.instance.registerCommand(new ExportCommand(COORDINATOR));
        LOGGER.info("[jeiexport] Client JEI/HEI exporter {} initialized for Minecraft 1.12.2; " +
                "request file is jeiexport-request.json", VERSION);
    }
}
