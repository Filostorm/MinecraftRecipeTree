package com.recipetree.neiexport1710;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = GtnhNeiExportMod.MOD_ID,
        name = "Recipe Tree GTNH NEI Exporter",
        version = "1.0.151",
        acceptableRemoteVersions = "*",
        dependencies = "required-after:NotEnoughItems"
)
@SideOnly(Side.CLIENT)
public final class GtnhNeiExportMod {
    static final String MOD_ID = "gtnhneiexport";
    static final Logger LOGGER = LogManager.getLogger("gtnh-nei-export");
    static final ExportCoordinator COORDINATOR = new ExportCoordinator();
    static final NeiFailureMonitor NEI_FAILURE_MONITOR = new NeiFailureMonitor();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NEI_FAILURE_MONITOR.install();
        FMLCommonHandler.instance().bus().register(COORDINATOR);
        MinecraftForge.EVENT_BUS.register(COORDINATOR);
        LOGGER.info(
                "[gtnh-nei-export] Client-only exporter ready on FML and Forge event buses; "
                        + "polling neiexport-request.json "
                        + "(disable with -Dgtnh.neiexport.auto=false)");
    }
}
