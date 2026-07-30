package com.recipetree.reiexport118;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RecipePhaseTest {
    @Test
    void nullCategoryIconViolatesThePublicationCompletenessContract() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> RecipePhase.requireCategoryIcon(null)
        );

        assertEquals(
                "REI DisplayCategory.getIcon() returned null; native category-icon completeness is required",
                failure.getMessage()
        );
    }
}
