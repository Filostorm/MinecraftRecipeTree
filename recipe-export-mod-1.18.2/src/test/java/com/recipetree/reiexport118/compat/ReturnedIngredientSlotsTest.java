package com.recipetree.reiexport118.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReturnedIngredientSlotsTest {
    @Test
    void movesAnExactlyReturnedMoldOutOfMaterialInputsAndOutputs() {
        JsonArray inputs = array("""
                [
                  [["item|test:metal", 4]],
                  [["item|test:mold", 1]]
                ]
                """);
        JsonArray outputs = array("""
                [
                  [["item|test:pressed_plate", 4]],
                  [["item|test:mold", 1]]
                ]
                """);

        ReturnedIngredientSlots.Resolution resolution =
                ReturnedIngredientSlots.extract(inputs, outputs);

        assertEquals(array("[[[\"item|test:metal\",4]]]"), resolution.materialInputs());
        assertEquals(array("[[[\"item|test:pressed_plate\",4]]]"), resolution.outputs());
        assertEquals(array("[[[\"item|test:mold\",1]]]"), resolution.returnedInputs());
        assertEquals(1, resolution.returnedSlotCount());
    }

    @Test
    void preservesInputsWhenTheReturnedQuantityIsNotExact() {
        JsonArray inputs = array("[[[\"item|test:container\",2]]]");
        JsonArray outputs = array("[[[\"item|test:container\",1]]]");

        ReturnedIngredientSlots.Resolution resolution =
                ReturnedIngredientSlots.extract(inputs, outputs);

        assertEquals(inputs, resolution.materialInputs());
        assertEquals(outputs, resolution.outputs());
        assertEquals(0, resolution.returnedSlotCount());
    }

    @Test
    void doesNotDuplicateAnExistingCatalystSlot() {
        JsonArray catalysts = array("[[[\"item|test:mold\",1]]]");

        ReturnedIngredientSlots.appendUnique(
                catalysts,
                array("[[[\"item|test:mold\",1]]]")
        );

        assertEquals(1, catalysts.size());
    }

    private static JsonArray array(String json) {
        return JsonParser.parseString(json).getAsJsonArray();
    }
}
