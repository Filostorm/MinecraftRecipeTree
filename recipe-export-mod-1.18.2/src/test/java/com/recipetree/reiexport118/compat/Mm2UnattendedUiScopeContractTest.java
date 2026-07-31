package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Mm2UnattendedUiScopeContractTest {
    @Test
    void recognizesOnlyTheExactVanillaPauseScreenForPreClaimNormalization() {
        assertTrue(Mm2UnattendedUiScope.isExactVanillaPauseScreenClassName(
                "net.minecraft.client.gui.screens.PauseScreen"));
        assertFalse(Mm2UnattendedUiScope.isExactVanillaPauseScreenClassName(null));
        assertFalse(Mm2UnattendedUiScope.isExactVanillaPauseScreenClassName(
                "example.CustomPauseScreen"));
        assertFalse(Mm2UnattendedUiScope.isExactVanillaPauseScreenClassName(
                "net.minecraft.client.gui.screens.inventory.InventoryScreen"));
    }
}
