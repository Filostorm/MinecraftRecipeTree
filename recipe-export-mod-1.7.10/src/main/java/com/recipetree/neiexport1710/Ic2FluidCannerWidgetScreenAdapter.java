package com.recipetree.neiexport1710;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Supplies the real NEI recipe-screen context required by IC2's fluid-canner foreground.
 *
 * <p>IC2 2.2.828 unconditionally casts {@code Minecraft.currentScreen} to
 * {@link GuiRecipe} while drawing its fluid tooltip. An exporter-owned
 * {@code NEIRecipeWidget} normally has no screen, even when the mouse is outside the tooltip.
 * This adapter uses NEI's public recipe-GUI factory for the exact pinned category, initializes
 * it without displaying it, and exposes it only for the duration of the widget draw.</p>
 */
final class Ic2FluidCannerWidgetScreenAdapter {
    static final String HANDLER =
            "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler";
    static final String LOAD_IDENTIFIER = "ic2.fluidcanner";
    static final String CONTRACT =
            "ic2-2.2.828-fluid-canner-nei-screen-context-v2";

    private Ic2FluidCannerWidgetScreenAdapter() {}

    static boolean matches(ICraftingHandler handler, String loadIdentifier) {
        return handler != null
                && HANDLER.equals(handler.getClass().getName())
                && LOAD_IDENTIFIER.equals(loadIdentifier);
    }

    static void draw(ICraftingHandler handler,
                     String loadIdentifier,
                     int sourceIndex,
                     OffscreenRenderer.DrawCall drawCall) throws Exception {
        if (!matches(handler, loadIdentifier)) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER",
                    "IC2 fluid-canner screen adapter was invoked outside its pinned contract");
        }
        if (sourceIndex < 0 || sourceIndex >= handler.numRecipes()) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER",
                    "IC2 fluid-canner screen adapter received invalid recipe index "
                            + sourceIndex + " of " + handler.numRecipes());
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previous = minecraft.currentScreen;
        GuiRecipe<?> context = GuiCraftingRecipe.createRecipeGui(
                loadIdentifier, false, new Object[0]);
        if (context == null) {
            throw new ExportFailure(
                    "RECIPE_WIDGET_RENDER",
                    "NEI did not construct the pinned IC2 fluid-canner recipe screen");
        }
        context.setWorldAndResolution(
                minecraft, minecraft.displayWidth, minecraft.displayHeight);
        minecraft.currentScreen = context;
        try {
            drawCall.draw();
        } finally {
            minecraft.currentScreen = previous;
        }
        GtnhNeiExportMod.LOGGER.info(
                "[gtnh-nei-export] Rendered IC2 fluid-canner widget with exact transient "
                        + "NEI screen context; sourceIndex={}, contract={}",
                sourceIndex, CONTRACT);
    }
}
