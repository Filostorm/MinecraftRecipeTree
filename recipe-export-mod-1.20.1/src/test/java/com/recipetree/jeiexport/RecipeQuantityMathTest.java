package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void reportsTheWholeRecipeBatchWhenItExceedsDemand() {
        long crafts = RecipeQuantityMath.craftsFor(90, 180);
        assertEquals(1, crafts);
        assertEquals(180, RecipeQuantityMath.producedTotal(180, crafts));
    }

    @Test
    void convertsFluidDemandIntoRecipeRunsUsingMillibucketYield() {
        long crafts = RecipeQuantityMath.craftsFor(2000, 500);
        assertEquals(4, crafts);
        assertEquals(4, RecipeQuantityMath.inputTotal(1, crafts));
        assertEquals(2000, RecipeQuantityMath.producedTotal(500, crafts));
    }

    @Test
    void saturatesInsteadOfWrappingLargeTreesNegative() {
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.inputTotal(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.producedTotal(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.safeAdd(Long.MAX_VALUE, 1));
    }

    @Test
    void scrollsRequestedAmountsByOneWithinTheEditableRange() {
        assertEquals(999, RecipeQuantityMath.MAX_REQUESTED_AMOUNT);
        assertEquals(2, RecipeQuantityMath.adjustRequestedAmount(1, 1));
        assertEquals(1, RecipeQuantityMath.adjustRequestedAmount(2, -1));
        assertEquals(1, RecipeQuantityMath.adjustRequestedAmount(1, -1));
        assertEquals(
                RecipeQuantityMath.MAX_REQUESTED_AMOUNT,
                RecipeQuantityMath.adjustRequestedAmount(RecipeQuantityMath.MAX_REQUESTED_AMOUNT, 1));
    }

    @Test
    void rejectsInvalidRequestedAmountScrollInput() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeQuantityMath.adjustRequestedAmount(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeQuantityMath.adjustRequestedAmount(1000, -1));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeQuantityMath.adjustRequestedAmount(1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeQuantityMath.adjustRequestedAmount(1, Double.NaN));
    }
}
