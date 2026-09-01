package com.recipetree.jeiexport112;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RecipeTreeAlternativeCorrelationTest {
    private static final String CONTAINER_WRAPPER =
            "cofh.thermalexpansion.plugins.jei.machine.transposer." +
                    "TransposerRecipeWrapperContainer";
    private static final String MULTI_WRAPPER =
            "cofh.thermalexpansion.plugins.jei.machine.transposer." +
                    "TransposerRecipeWrapperMulti";

    @Test
    public void transposerContainerKeepsTheFluidAndContainerAtTheFocusedIndex() {
        List<RecipeTreeViewerBridge.Slot> inputs = Collections.singletonList(slot(
                "item|minecraft:water_bucket",
                "item|minecraft:lava_bucket",
                "item|forge:bucketfilled|rosite"));
        List<RecipeTreeViewerBridge.Slot> outputs = Arrays.asList(
                slot("item|minecraft:bucket", "item|minecraft:bucket", "item|minecraft:bucket"),
                slot("fluid|fluid:water", "fluid|fluid:lava", "fluid|fluid:rosite"));

        RecipeTreeViewerBridge.CorrelatedSlots result =
                RecipeTreeViewerBridge.correlateAlternatives(
                        CONTAINER_WRAPPER, "fluid|fluid:rosite", inputs, outputs);

        assertNull(result.failure);
        assertEquals(2, result.selectedIndex);
        assertEquals("item|forge:bucketfilled|rosite",
                result.inputs.get(0).getAlternatives().get(0).getKey());
        assertEquals("item|minecraft:bucket",
                result.outputs.get(0).getAlternatives().get(0).getKey());
        assertEquals("fluid|fluid:rosite",
                result.outputs.get(1).getAlternatives().get(0).getKey());
        assertEquals(1, result.inputs.get(0).getAlternatives().size());
        assertEquals(1, result.outputs.get(1).getAlternatives().size());
    }

    @Test
    public void transposerMultiUsesTheSameCorrelationPolicy() {
        List<RecipeTreeViewerBridge.Slot> inputs = Collections.singletonList(slot(
                "item|water", "item|lava", "item|resonant_ender"));
        List<RecipeTreeViewerBridge.Slot> outputs = Collections.singletonList(slot(
                "fluid|water", "fluid|lava", "fluid|resonant_ender"));

        RecipeTreeViewerBridge.CorrelatedSlots result =
                RecipeTreeViewerBridge.correlateAlternatives(
                        MULTI_WRAPPER, "item|lava", inputs, outputs);

        assertNull(result.failure);
        assertEquals(1, result.selectedIndex);
        assertEquals("item|lava", result.inputs.get(0).getAlternatives().get(0).getKey());
        assertEquals("fluid|lava", result.outputs.get(0).getAlternatives().get(0).getKey());
    }

    @Test
    public void unrelatedTagAlternativesAreNeverZippedByCoincidence() {
        List<RecipeTreeViewerBridge.Slot> inputs = Collections.singletonList(slot(
                "item|copper_a", "item|copper_b"));
        List<RecipeTreeViewerBridge.Slot> outputs = Collections.singletonList(slot(
                "item|plate_a", "item|plate_b"));

        RecipeTreeViewerBridge.CorrelatedSlots result =
                RecipeTreeViewerBridge.correlateAlternatives(
                        "example.jei.PlateRecipeWrapper", "item|plate_b", inputs, outputs);

        assertNull(result.failure);
        assertEquals(-1, result.selectedIndex);
        assertSame(inputs, result.inputs);
        assertSame(outputs, result.outputs);
    }

    @Test
    public void ambiguousOrDriftedTransposerDataFailsLoudlyWithoutChoosingWater() {
        List<RecipeTreeViewerBridge.Slot> inputs = Collections.singletonList(slot(
                "item|water", "item|water", "item|water"));
        List<RecipeTreeViewerBridge.Slot> outputs = Collections.singletonList(slot(
                "fluid|water", "fluid|lava"));

        RecipeTreeViewerBridge.CorrelatedSlots result =
                RecipeTreeViewerBridge.correlateAlternatives(
                        CONTAINER_WRAPPER, "fluid|rosite", inputs, outputs);

        assertNotNull(result.failure);
        assertEquals(-1, result.selectedIndex);
        assertSame(inputs, result.inputs);
        assertSame(outputs, result.outputs);
    }

    private static RecipeTreeViewerBridge.Slot slot(String... keys) {
        RecipeTreeViewerBridge.Ingredient[] ingredients =
                new RecipeTreeViewerBridge.Ingredient[keys.length];
        for (int index = 0; index < keys.length; index++) {
            ingredients[index] = new RecipeTreeViewerBridge.Ingredient(
                    null, keys[index], keys[index], keys[index], BigDecimal.ONE);
        }
        return new RecipeTreeViewerBridge.Slot(Arrays.asList(ingredients));
    }
}
