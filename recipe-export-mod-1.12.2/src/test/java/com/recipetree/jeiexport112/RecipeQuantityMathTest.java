package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecipeQuantityMathTest {
    @Test
    public void roundsCraftsUpUsingTheRecipeOutputYield() {
        assertEquals(16L, RecipeQuantityMath.craftsFor(64, 4));
        assertEquals(17L, RecipeQuantityMath.craftsFor(65, 4));
    }

    @Test
    public void multipliesEachInputByTheRequiredCraftCount() {
        assertEquals(48L, RecipeQuantityMath.inputTotal(3, 16));
    }

    @Test
    public void reportsTheWholeRecipeBatchWhenItExceedsDemand() {
        long crafts = RecipeQuantityMath.craftsFor(90, 180);
        assertEquals(1L, crafts);
        assertEquals(180L, RecipeQuantityMath.producedTotal(180, crafts));
    }

    @Test
    public void convertsFluidDemandIntoRecipeRunsUsingMillibucketYield() {
        long crafts = RecipeQuantityMath.craftsFor(2000, 500);
        assertEquals(4L, crafts);
        assertEquals(4L, RecipeQuantityMath.inputTotal(1, crafts));
        assertEquals(2000L, RecipeQuantityMath.producedTotal(500, crafts));
    }

    @Test
    public void reusesBatchSurplusBeforeRunningRepeatedChemicalRecipes() {
        long availableSurplus = 0;
        long totalCrafts = 0;
        long grossSharedSurplus = 0;
        for (int node = 0; node < 6; node++) {
            long covered = Math.min(40, availableSurplus);
            availableSurplus -= covered;
            long remaining = RecipeQuantityMath.remainingAfterSupply(40, covered);
            long crafts = RecipeQuantityMath.craftsForRemaining(remaining, 80);
            long surplus = RecipeQuantityMath.surplusAfterCrafts(remaining, 80, crafts);
            totalCrafts += crafts;
            grossSharedSurplus += surplus;
            availableSurplus += surplus;
        }

        assertEquals(3L, totalCrafts);
        assertEquals(120L, grossSharedSurplus);
        assertEquals(0L, availableSurplus);
    }

    @Test
    public void partialByproductCoverageReducesCraftsAndKeepsOnlyNewSurplus() {
        long remaining = RecipeQuantityMath.remainingAfterSupply(100, 30);
        long crafts = RecipeQuantityMath.craftsForRemaining(remaining, 50);

        assertEquals(70L, remaining);
        assertEquals(2L, crafts);
        assertEquals(30L, RecipeQuantityMath.surplusAfterCrafts(remaining, 50, crafts));
        assertEquals(0L, RecipeQuantityMath.craftsForRemaining(0, 50));
    }

    @Test
    public void saturatesInsteadOfWrappingLargeTreesNegative() {
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.inputTotal(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.producedTotal(Long.MAX_VALUE, 2));
        assertEquals(Long.MAX_VALUE, RecipeQuantityMath.safeAdd(Long.MAX_VALUE, 1));
    }

    @Test
    public void scrollsRequestedAmountsByOneWithinTheEditableRange() {
        assertEquals(999L, RecipeQuantityMath.MAX_REQUESTED_AMOUNT);
        assertEquals(2L, RecipeQuantityMath.adjustRequestedAmount(1, 1));
        assertEquals(1L, RecipeQuantityMath.adjustRequestedAmount(2, -1));
        assertEquals(1L, RecipeQuantityMath.adjustRequestedAmount(1, -1));
        assertEquals(
                RecipeQuantityMath.MAX_REQUESTED_AMOUNT,
                RecipeQuantityMath.adjustRequestedAmount(
                        RecipeQuantityMath.MAX_REQUESTED_AMOUNT, 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRequestedAmountsBelowTheEditableRange() {
        RecipeQuantityMath.adjustRequestedAmount(0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRequestedAmountsAboveTheEditableRange() {
        RecipeQuantityMath.adjustRequestedAmount(1000, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroScrollDelta() {
        RecipeQuantityMath.adjustRequestedAmount(1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteScrollDelta() {
        RecipeQuantityMath.adjustRequestedAmount(1, Double.NaN);
    }
}
