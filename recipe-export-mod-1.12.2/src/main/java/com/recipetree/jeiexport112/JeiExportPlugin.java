package com.recipetree.jeiexport112;

import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientRegistry;

/** Captures both halves of the JEI 4.x API. The runtime does not expose the ingredient registry. */
@JEIPlugin
public final class JeiExportPlugin implements IModPlugin {
    private static volatile IIngredientRegistry ingredientRegistry;
    private static volatile IJeiRuntime runtime;
    private static volatile RecipeTreeViewerBridge viewerBridge;
    private static volatile boolean viewerBridgeCreationFailed;

    @Override
    public void register(IModRegistry registry) {
        ingredientRegistry = registry.getIngredientRegistry();
        viewerBridgeCreationFailed = false;
        JeiExportMod.LOGGER.info("[jeiexport] Captured JEI/HEI ingredient registry");
        createViewerBridgeIfReady();
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RecipeTreeViewerBridge previous = viewerBridge;
        if (previous != null) previous.clearCaches();
        viewerBridge = null;
        viewerBridgeCreationFailed = false;
        runtime = jeiRuntime;
        JeiExportMod.LOGGER.info("[jeiexport] Captured JEI/HEI runtime");
        createViewerBridgeIfReady();
    }

    static IIngredientRegistry getIngredientRegistry() {
        return ingredientRegistry;
    }

    static IJeiRuntime getRuntime() {
        return runtime;
    }

    static RecipeTreeViewerBridge getViewerBridge() {
        RecipeTreeViewerBridge current = viewerBridge;
        if (current != null) return current;
        createViewerBridgeIfReady();
        return viewerBridge;
    }

    static void clearViewerCaches() {
        RecipeTreeViewerBridge current = viewerBridge;
        if (current != null) current.clearCaches();
    }

    private static synchronized void createViewerBridgeIfReady() {
        if (viewerBridge != null || viewerBridgeCreationFailed
                || runtime == null || ingredientRegistry == null) return;
        try {
            viewerBridge = new RecipeTreeViewerBridge(runtime, ingredientRegistry);
            JeiExportMod.LOGGER.info("[jeiexport] Live Recipe Tree viewer bridge is ready");
        } catch (RuntimeException error) {
            viewerBridgeCreationFailed = true;
            JeiExportMod.LOGGER.warn(
                    "[jeiexport] Could not initialize the live Recipe Tree viewer bridge; "
                            + "the exporter remains available but G cannot open the viewer",
                    error);
        }
    }
}
