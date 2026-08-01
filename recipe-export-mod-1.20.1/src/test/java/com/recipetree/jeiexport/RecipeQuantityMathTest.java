package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeQuantityMathTest {
    @Test
    void roundsCraftsUpUsingTheRecipeOutputYield() {
        assertEquals(16, RecipeQuantityMath.craftsFor(64, 4));
        assertEquals(17, RecipeQuantityMath.craftsFor(65, 4));
    }

    @Test
    void multipliesEachInputByTheRequiredCraftCount() {
        assertEquals(48, RecipeQuantityMath.inputTotal(3, 16));
    }

    @Test
    void saturatesInsteadOfWrappingLargeTreesNegative() {
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.inputTotal(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.safeAdd(Long.MAX_VALUE, 1));
    }
}
