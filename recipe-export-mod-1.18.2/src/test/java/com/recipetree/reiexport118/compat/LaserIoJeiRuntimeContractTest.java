package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LaserIoJeiRuntimeContractTest {
    @Test
    void appliesOnlyToTheExactObservedRuntimeTuple() {
        assertTrue(LaserIoJeiRuntimeContract.isApplicable(
                "1.18.2", "40.2.17", "1.4.5", "8.4.778", "8.0.89", "9.9999"
        ));
        assertFalse(LaserIoJeiRuntimeContract.isApplicable(
                "1.18.2", "40.2.18", "1.4.5", "8.4.778", "8.0.89", "9.9999"
        ));
        assertFalse(LaserIoJeiRuntimeContract.isApplicable(
                "1.18.2", "40.2.17", "1.4.6", "8.4.778", "8.0.89", "9.9999"
        ));
        assertFalse(LaserIoJeiRuntimeContract.isApplicable(
                "1.18.2", "40.2.17", "1.4.5", "8.4.779", "8.0.89", "9.9999"
        ));
        assertFalse(LaserIoJeiRuntimeContract.isApplicable(
                "1.18.2", "40.2.17", "1.4.5", "8.4.778", "8.0.90", "9.9999"
        ));
    }

    @Test
    void preservesTheFourAbsentAndFourHiddenRecipeMappings() {
        assertEquals(4, LaserIoJeiRuntimeContract.packRemovedCardResets().size());
        assertEquals(4, LaserIoJeiRuntimeContract.presentFilterResets().size());
        assertEquals(8, LaserIoJeiRuntimeContract.allResetRecipeIds().size());
        assertEquals(
                "mbm2:laserio_card_item",
                LaserIoJeiRuntimeContract.packRemovedCardResets()
                        .get("laserio:card_item_nbtclear")
        );
        assertEquals(
                "laserio:filter_mod",
                LaserIoJeiRuntimeContract.presentFilterResets()
                        .get("laserio:filter_mod_nbtclear")
        );
    }

    @Test
    void acceptsOnlyTheExactObservedRecipeCorpus() {
        LaserIoJeiRuntimeContract.requireExactCorpus(
                new LinkedHashSet<>(LaserIoJeiRuntimeContract.presentFilterResets().keySet()),
                new LinkedHashSet<>(LaserIoJeiRuntimeContract.packRemovedCardResets().values())
        );

        Set<String> missingFilter = new LinkedHashSet<>(
                LaserIoJeiRuntimeContract.presentFilterResets().keySet()
        );
        missingFilter.remove("laserio:filter_mod_nbtclear");
        assertThrows(
                IllegalStateException.class,
                () -> LaserIoJeiRuntimeContract.requireExactCorpus(
                        missingFilter,
                        new LinkedHashSet<>(
                                LaserIoJeiRuntimeContract.packRemovedCardResets().values()
                        )
                )
        );
    }

    @Test
    void pinsTheAuditedPluginAndCompatBytecode() {
        assertEquals(
                64,
                LaserIoJeiRuntimeContract.PLUGIN_CLASS_SHA256.length()
        );
        assertEquals(
                64,
                LaserIoJeiRuntimeContract.RUNTIME_CLASS_SHA256.length()
        );
        assertEquals(
                64,
                LaserIoJeiRuntimeContract.RECIPE_MANAGER_CLASS_SHA256.length()
        );
    }
}
