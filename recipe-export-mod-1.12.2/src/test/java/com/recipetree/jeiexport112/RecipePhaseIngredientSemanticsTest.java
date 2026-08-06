package com.recipetree.jeiexport112;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class RecipePhaseIngredientSemanticsTest {
    @Test
    public void coalescesAFlattenedOreDictionaryChoice() {
        RecipePhase.RecipeData data = new RecipePhase.RecipeData();
        data.inputs.add(slot("ore:oreIron", "item|minecraft:iron_ore"));
        data.inputs.add(slot("ore:oreIron", "item|abyssalcraft:abyiroore"));
        data.inputs.add(slot("ore:oreIron", "item|cyclicmagic:nether_iron_ore"));

        RecipePhase.coalesceFlattenedLogicalAlternatives(data.inputs);

        assertEquals(1, data.inputs.size());
        assertEquals(3, data.inputs.get(0).pairs.size());
    }

    @Test
    public void preservesRepeatedRequirementsOfTheSameOreDictionaryChoice() {
        RecipePhase.RecipeData data = new RecipePhase.RecipeData();
        data.inputs.add(slot("ore:oreIron", "item|first:iron_ore"));
        data.inputs.add(slot("ore:oreIron", "item|second:iron_ore"));
        data.inputs.add(slot("ore:oreIron", "item|first:iron_ore"));
        data.inputs.add(slot("ore:oreIron", "item|second:iron_ore"));

        RecipePhase.coalesceFlattenedLogicalAlternatives(data.inputs);

        assertEquals(2, data.inputs.size());
        assertEquals(2, data.inputs.get(0).pairs.size());
        assertEquals(2, data.inputs.get(1).pairs.size());
    }

    @Test
    public void promotesAnUnchangedReturnedItemToOneRetainedPrerequisite() {
        RecipePhase.RecipeData data = new RecipePhase.RecipeData();
        data.inputs.add(slot(null, "item|test:mold"));
        data.inputs.add(slot(null, "item|test:metal"));
        data.outputs.add(slot(null, "item|test:plate"));
        data.outputs.add(slot(null, "item|test:mold"));

        RecipePhase.promoteReturnedIngredients(data);

        assertEquals(1, data.inputs.size());
        assertEquals("item|test:metal", data.inputs.get(0).pairs.get(0).key);
        assertEquals(1, data.outputs.size());
        assertEquals("item|test:plate", data.outputs.get(0).pairs.get(0).key);
        assertEquals(1, data.catalysts.size());
        assertEquals("item|test:mold", data.catalysts.get(0).pairs.get(0).key);
    }

    @Test
    public void doesNotTreatAnIncreasedOutputAsAnUnchangedReturn() {
        RecipePhase.RecipeData data = new RecipePhase.RecipeData();
        data.inputs.add(slot(null, "item|test:seed", 1));
        data.outputs.add(slot(null, "item|test:seed", 2));

        RecipePhase.promoteReturnedIngredients(data);

        assertEquals(1, data.inputs.size());
        assertEquals(1, data.outputs.size());
        assertEquals(0, data.catalysts.size());
    }

    private static RecipePhase.SlotData slot(String identity, String key) {
        return slot(identity, key, 1);
    }

    private static RecipePhase.SlotData slot(String identity, String key, int amount) {
        RecipePhase.SlotData slot = new RecipePhase.SlotData();
        slot.logicalIdentity = identity;
        slot.pairs.add(new RecipePhase.IngredientPair(key, BigDecimal.valueOf(amount)));
        return slot;
    }
}
