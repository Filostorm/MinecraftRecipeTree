package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class IndustrialForegoingScreenContractTest {
    @Test
    void appliesOnlyToTheExactAuditedRuntimeTuple() {
        assertTrue(IndustrialForegoingScreenContract.isApplicable(
                "1.18.2", "40.2.17", "3.3.1.7", "3.5.11"));
        assertFalse(IndustrialForegoingScreenContract.isApplicable(
                "1.18.2", "40.2.18", "3.3.1.7", "3.5.11"));
        assertFalse(IndustrialForegoingScreenContract.isApplicable(
                "1.18.2", "40.2.17", "3.3.1.8", "3.5.11"));
        assertFalse(IndustrialForegoingScreenContract.isApplicable(
                "1.18.2", "40.2.17", "3.3.1.7", "3.5.12"));
    }

    @Test
    void scopesOnlyTheTwentyThreeObservedScreenDependentCategories() {
        assertEquals(23,
                IndustrialForegoingScreenContract.SCREEN_DEPENDENT_CATEGORY_IDS.size());
        for (String categoryId
                : IndustrialForegoingScreenContract.SCREEN_DEPENDENT_CATEGORY_IDS) {
            assertTrue(IndustrialForegoingScreenContract.requiresScreen(categoryId), categoryId);
        }
        assertFalse(IndustrialForegoingScreenContract.requiresScreen(
                "industrialforegoing:bioreactor"));
        assertFalse(IndustrialForegoingScreenContract.requiresScreen(
                "industrialforegoing:future_mycelial_renderer"));
        assertFalse(IndustrialForegoingScreenContract.requiresScreen(
                "example:dissolution"));
    }

    @Test
    void pinsAllEightNativeJeiRendererClassesAndRejectsMalformedScreenDimensions() {
        assertEquals(8, IndustrialForegoingScreenContract.RENDERER_CLASS_SHA256.size());
        for (var pin : IndustrialForegoingScreenContract.RENDERER_CLASS_SHA256.entrySet()) {
            assertTrue(pin.getKey().endsWith(".class"), pin.getKey());
            assertTrue(pin.getValue().matches("[0-9a-f]{64}"), pin.getKey());
        }
        IndustrialForegoingScreenContract.requireLogicalDimensions(168, 90);
        assertThrows(IllegalArgumentException.class, () ->
                IndustrialForegoingScreenContract.requireLogicalDimensions(0, 90));
        assertThrows(IllegalArgumentException.class, () ->
                IndustrialForegoingScreenContract.requireLogicalDimensions(168, 4097));
    }

}
