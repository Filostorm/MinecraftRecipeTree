package com.recipetree.jeiexport112;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class RecipeTreeBrewingSemanticsTest {
    private static final String VANILLA_BREWING_WRAPPER =
            "mezz.jei.plugins.vanilla.brewing.BrewingRecipeWrapper";

    @Test
    public void threeVisualBottleSlotsCountAsOneConsumedPotion() {
        RecipeTreeViewerBridge.Slot potion = slot("item|minecraft:potion|awkward");
        RecipeTreeViewerBridge.Slot ingredient = slot("item|minecraft:blaze_powder");
        List<RecipeTreeViewerBridge.Slot> inputs = Arrays.asList(
                potion, potion, potion, ingredient);

        List<RecipeTreeViewerBridge.Slot> normalized =
                RecipeTreeViewerBridge.normalizeBrewingInputs(
                        VANILLA_BREWING_WRAPPER, inputs);

        assertEquals(2, normalized.size());
        assertSame(potion, normalized.get(0));
        assertSame(ingredient, normalized.get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void changedBrewingWrapperShapeFailsInsteadOfGuessingQuantities() {
        RecipeTreeViewerBridge.normalizeBrewingInputs(
                VANILLA_BREWING_WRAPPER,
                Arrays.asList(
                        slot("item|potion_a"),
                        slot("item|potion_b"),
                        slot("item|potion_a"),
                        slot("item|ingredient")));
    }

    @Test
    public void unrelatedWrappersAreUntouched() {
        List<RecipeTreeViewerBridge.Slot> inputs = Arrays.asList(
                slot("item|one"), slot("item|two"));

        assertSame(inputs, RecipeTreeViewerBridge.normalizeBrewingInputs(
                "example.machine.RecipeWrapper", inputs));
    }

    private static RecipeTreeViewerBridge.Slot slot(String key) {
        return new RecipeTreeViewerBridge.Slot(Arrays.asList(
                new RecipeTreeViewerBridge.Ingredient(
                        null, key, key, key, BigDecimal.ONE)));
    }
}
