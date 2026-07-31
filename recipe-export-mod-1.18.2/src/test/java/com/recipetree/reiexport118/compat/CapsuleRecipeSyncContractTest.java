package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CapsuleRecipeSyncContractTest {
    @Test
    void appliesOnlyToTheExactObservedRuntimeTuple() {
        assertTrue(CapsuleRecipeSyncContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-6.0.99", "8.4.778", "8.0.89", "4.12.94"
        ));
        assertFalse(CapsuleRecipeSyncContract.isApplicable(
                "1.18.2", "40.2.18", "1.18.2-6.0.99", "8.4.778", "8.0.89", "4.12.94"
        ));
        assertFalse(CapsuleRecipeSyncContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-6.0.100", "8.4.778", "8.0.89", "4.12.94"
        ));
        assertFalse(CapsuleRecipeSyncContract.isApplicable(
                "1.18.2", "40.2.17", "1.18.2-6.0.99", "8.4.778", "8.0.89", "4.12.95"
        ));
    }

    @Test
    void requiresTheWholeHydratedCapsuleCorpus() {
        var complete = new CapsuleRecipeSyncContract.HydratedSnapshot(
                "capsule:upgrade", "capsule:recovery", "capsule:blueprint_change",
                1, 1, 1, 26
        );
        assertDoesNotThrow(() -> CapsuleRecipeSyncContract.requireHydratedSnapshot(complete));

        var missingPrefabs = new CapsuleRecipeSyncContract.HydratedSnapshot(
                "capsule:upgrade", "capsule:recovery", "capsule:blueprint_change",
                1, 1, 1, 0
        );
        assertThrows(
                IllegalStateException.class,
                () -> CapsuleRecipeSyncContract.requireHydratedSnapshot(missingPrefabs)
        );

        var wrongUpgrade = new CapsuleRecipeSyncContract.HydratedSnapshot(
                "capsule:upgrade_v2", "capsule:recovery", "capsule:blueprint_change",
                1, 1, 1, 26
        );
        assertThrows(
                IllegalStateException.class,
                () -> CapsuleRecipeSyncContract.requireHydratedSnapshot(wrongUpgrade)
        );
    }

    @Test
    void pinsTheJarAndAllOrderingParticipants() {
        assertEquals(64, CapsuleRecipeSyncContract.CAPSULE_JAR_SHA256.length());
        assertEquals(64, CapsuleRecipeSyncContract.CAPSULE_ITEMS_SHA256.length());
        assertEquals(64, CapsuleRecipeSyncContract.CAPSULE_PLUGIN_SHA256.length());
        assertEquals(64, CapsuleRecipeSyncContract.CAPSULE_FORGE_SUBSCRIBER_SHA256.length());
        assertEquals(64, CapsuleRecipeSyncContract.REI_CORE_CLIENT_SHA256.length());
        assertEquals(64, CapsuleRecipeSyncContract.ARCHITECTURY_EVENT_HANDLER_SHA256.length());
    }
}
