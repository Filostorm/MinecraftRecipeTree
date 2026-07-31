package com.recipetree.reiexport118.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DelightfulLivingTierContractTest {
    @Test
    void acceptsDelightfulTwoSixLivingTierExactTuple() {
        assertDoesNotThrow(() -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 2, 192, 6.0F, 2.0F, 18
        ));
    }

    @Test
    void rejectsUnknownNullRepairTier() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DelightfulLivingTierContract.requireExact(
                        "FUTURE_SELF_REPAIRING", 27, 2, 192, 6.0F, 2.0F, 18
                )
        );
        assertTrue(exception.getMessage().contains("unknown null-repair Delightful tier"));
    }

    @Test
    void rejectsEveryIntegerFieldDrift() {
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 28, 2, 192, 6.0F, 2.0F, 18
        ));
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 3, 192, 6.0F, 2.0F, 18
        ));
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 2, 193, 6.0F, 2.0F, 18
        ));
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 2, 192, 6.0F, 2.0F, 19
        ));
    }

    @Test
    void rejectsEveryFloatingPointFieldDrift() {
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 2, 192, 6.1F, 2.0F, 18
        ));
        assertThrows(IllegalStateException.class, () -> DelightfulLivingTierContract.requireExact(
                "LIVING", 27, 2, 192, 6.0F, 2.1F, 18
        ));
    }
}
