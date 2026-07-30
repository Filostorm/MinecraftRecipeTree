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

    @Override
    public void register(IModRegistry registry) {
        ingredientRegistry = registry.getIngredientRegistry();
        JeiExportMod.LOGGER.info("[jeiexport] Captured JEI/HEI ingredient registry");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        JeiExportMod.LOGGER.info("[jeiexport] Captured JEI/HEI runtime");
    }

    static IIngredientRegistry getIngredientRegistry() {
        return ingredientRegistry;
    }

    static IJeiRuntime getRuntime() {
        return runtime;
    }
}
