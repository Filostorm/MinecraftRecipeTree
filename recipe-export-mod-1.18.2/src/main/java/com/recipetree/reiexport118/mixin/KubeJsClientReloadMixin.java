package com.recipetree.reiexport118.mixin;

import com.recipetree.reiexport118.compat.KubeJsTooltipConcurrencyContract;
import com.recipetree.reiexport118.compat.KubeJsTooltipPublicationRepair;
import dev.latvian.mods.kubejs.KubeJS;
import dev.latvian.mods.kubejs.client.KubeJSClientEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/** Replays the exact pinned KubeJS reload body inside the exclusive tooltip lifecycle lease. */
@Pseudo
@Mixin(targets = KubeJsTooltipConcurrencyContract.RELOAD_TARGET_CLASS, remap = false)
public abstract class KubeJsClientReloadMixin {
    /**
     * @author Minecraft Recipe Tree
     * @reason Exact KubeJS build publishes a plain null and reloads scripts without coordinating
     * concurrent REI tooltip-cache readers.
     */
    @Overwrite(remap = false)
    public static void reloadClientScripts() {
        KubeJsTooltipPublicationRepair.runReload(() -> {
            KubeJSClientEventHandler.staticItemTooltips = null;
            KubeJS.clientScriptManager.unload();
            KubeJS.clientScriptManager.loadFromDirectory();
            KubeJS.clientScriptManager.load();
        });
    }
}
