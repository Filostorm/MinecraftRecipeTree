package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CompactCraftingInputAmountsTest {
    @Test
    void machineFrameStructureCountsReplaceJeiUnitCardinality() {
        List<CompactCraftingInputAmounts.ComponentAmount<String>> components = List.of(
                new CompactCraftingInputAmounts.ComponentAmount<>(
                        "aluminium_sheetmetal", "immersiveengineering:sheetmetal_aluminum", 8),
                new CompactCraftingInputAmounts.ComponentAmount<>(
                        "steel_sheetmetal", "immersiveengineering:sheetmetal_steel", 8),
                new CompactCraftingInputAmounts.ComponentAmount<>(
                        "invar_scaffolding", "kubejs:invar_scaffolding", 4)
        );
        List<List<String>> reiInputs = List.of(
                List.of("immersiveengineering:component_electronic_adv"),
                List.of("immersiveengineering:sheetmetal_aluminum"),
                List.of("immersiveengineering:sheetmetal_steel"),
                List.of("kubejs:invar_scaffolding")
        );

        assertEquals(
                Map.of(1, 8L, 2, 8L, 3, 4L),
                CompactCraftingInputAmounts.matchComponents(components, reiInputs));
    }

    @Test
    void missingComponentSlotRejectsPublicationInsteadOfFallingBackToOne() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> CompactCraftingInputAmounts.matchComponents(
                        List.of(new CompactCraftingInputAmounts.ComponentAmount<>(
                                "steel_sheetmetal",
                                "immersiveengineering:sheetmetal_steel",
                                8)),
                        List.of(List.of("immersiveengineering:sheetmetal_aluminum"))));

        assertEquals(
                "Compact Crafting component steel_sheetmetal"
                        + " could not be matched to a distinct REI input slot",
                failure.getMessage());
    }
}
