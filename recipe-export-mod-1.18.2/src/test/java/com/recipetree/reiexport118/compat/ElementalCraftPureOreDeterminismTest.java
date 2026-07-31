package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ElementalCraftPureOreDeterminismTest {
    @Test
    void expectedDomainMatchesThePinnedManagersPostProcessabilityDomain() {
        Set<String> expected = ElementalCraftPureOreDeterminism.expectedOres();

        assertEquals(25, expected.size());
        assertEquals(List.of(
                "forge:arcane_crystal", "mythicbotany:elementium", "forge:tin",
                "forbidden_arcanus:runic_darkstone", "forge:apatite",
                "mythicbotany:dragonstone", "forge:certus_quartz", "forge:osmium",
                "forge:inert_crystal", "forge:lapis", "forge:sulfur",
                "forge:netherite_scrap", "forge:redstone", "forge:gold",
                "forbidden_arcanus:runic_deepslate", "forge:niter", "forge:emerald",
                "forge:cheese", "forge:fluorite", "forbidden_arcanus:runic_stone",
                "forge:coal", "forge:quartz", "forge:diamond", "forge:uranium",
                "forge:cinnabar"), List.copyOf(expected));
        assertTrue(expected.contains("forge:osmium"));
        assertTrue(expected.contains("forge:uranium"));
        assertFalse(expected.contains("forge:platinum"),
                "platinum sources are coalesced into MM2's resolved osmium tag");
        assertFalse(expected.contains("forge:yellorium"),
                "yellorium is a source alias for MM2's resolved uranium domain");
    }

    @Test
    void expectedDomainIsImmutable() {
        Set<String> expected = ElementalCraftPureOreDeterminism.expectedOres();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> expected.remove("forge:uranium"));
    }

    @Test
    void expectedPurifierRecipeCountIncludesTheFiveDualFormDomains() {
        assertEquals(30, ElementalCraftPureOreDeterminism.expectedPurifierRecipeCount());
    }
}
