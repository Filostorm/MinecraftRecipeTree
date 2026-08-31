package com.recipetree.jeiexport112;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IngredientQuantityTest {
    @Test
    public void exactZeroIsPreservedForContextSpecificSemanticClassification() {
        assertEquals(BigDecimal.ZERO,
                IngredientQuantity.validatedAmount(0, "example.Fluid", "amount"));
        assertEquals(BigDecimal.ZERO,
                IngredientQuantity.validatedAmount(-0.0d, "example.Will", "getAmount()"));
    }

    @Test
    public void positiveDecimalQuantityRemainsExact() {
        assertEquals(new BigDecimal("0.125"),
                IngredientQuantity.validatedAmount(0.125d, "example.Energy", "getAmount()"));
        assertEquals("10000",
                IngredientQuantity.validatedAmount(10000, "example.Mana", "amount").toPlainString());
    }

    @Test
    public void negativeAndNonFiniteQuantitiesRemainHardFailures() {
        assertInvalid(-1, "finite and non-negative");
        assertInvalid(Double.NaN, "finite and non-negative");
        assertInvalid(Double.POSITIVE_INFINITY, "finite and non-negative");
    }

    @Test
    public void multiblockMadnessCustomIngredientPoliciesAreExact() {
        assertEquals("getConsumedEnergy", IngredientQuantity.explicitMethodAccessor(
                "modulardiversity.jei.ingredients.MekLaser"));
        assertEquals("getConsumedEmbers", IngredientQuantity.explicitMethodAccessor(
                "modulardiversity.jei.ingredients.Embers"));
        assertEquals("getConsumedMana", IngredientQuantity.explicitMethodAccessor(
                "modulardiversity.jei.ingredients.Mana"));
        assertEquals("energy", IngredientQuantity.explicitFieldAccessor(
                "requious.compat.jei.ingredient.Energy"));
        assertTrue(IngredientQuantity.isUnitValueType(
                "modulardiversity.jei.ingredients.DimensionIngredient"));
        assertTrue(IngredientQuantity.isUnitValueType(
                "modulardiversity.jei.ingredients.MysticalMechanics"));

        assertNull(IngredientQuantity.explicitMethodAccessor(
                "modulardiversity.jei.ingredients.Unknown"));
        assertNull(IngredientQuantity.explicitFieldAccessor(
                "requious.compat.jei.ingredient.Unknown"));
    }

    @Test
    public void runtimeNeutralCallerCanRejectUnknownUnitFallback() {
        try {
            IngredientQuantity.amount(new Object(), new IngredientQuantity.UnknownQuantityReporter() {
                @Override
                public void report(Class<?> ingredientClass) {
                    throw new IllegalArgumentException("unsupported " + ingredientClass.getName());
                }
            });
            fail("Expected runtime planner to reject an unknown quantity class");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported java.lang.Object"));
        }
    }

    private static void assertInvalid(Number number, String expectedMessage) {
        try {
            IngredientQuantity.validatedAmount(number, "example.Invalid", "amount");
            fail("Expected invalid quantity " + number + " to fail");
        } catch (IllegalArgumentException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains(expectedMessage));
        }
    }
}
