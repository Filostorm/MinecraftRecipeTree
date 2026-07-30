package com.recipetree.jeiexport112.compat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class MultiblockedScissorTransformTest {
    @Test
    public void mapsTheObservedMultiblockedInputPanelIntoTheRecipeFbo() {
        MultiblockedScissorTransform.Box mapped = MultiblockedScissorTransform.map(
                260, 342, 128, 128,
                2, 240,
                2, 92,
                -121, 4
        );

        assertBox(mapped, 18, 38, 128, 128);
    }

    @Test
    public void mapsASecondPanelWithoutClampingOrChangingItsDimensions() {
        // Logical panel (236, 5, 64, 64) under the same live 2x GUI transform.
        MultiblockedScissorTransform.Box mapped = MultiblockedScissorTransform.map(
                472, 342, 128, 128,
                2, 240,
                2, 92,
                -121, 4
        );

        assertBox(mapped, 230, 38, 128, 128);
    }

    @Test
    public void supportsDifferentLiveAndRecipeScaleFactors() {
        MultiblockedScissorTransform.Box mapped = MultiblockedScissorTransform.map(
                390, 513, 192, 192,
                3, 240,
                2, 92,
                -121, 4
        );

        assertBox(mapped, 18, 38, 128, 128);
    }

    @Test
    public void preservesNegativeOriginsForTheTemporaryFullScreenPopRectangle() {
        MultiblockedScissorTransform.Box mapped = MultiblockedScissorTransform.map(
                0, -480, 1280, 960,
                2, 240,
                2, 92,
                -121, 4
        );

        assertBox(mapped, -242, -784, 1280, 960);
    }

    @Test
    public void rejectsRawCoordinatesThatCannotHaveComeFromTheAuditedGuiScale() {
        try {
            MultiblockedScissorTransform.map(
                    261, 342, 128, 128,
                    2, 240,
                    2, 92,
                    -121, 4
            );
            fail("Expected indivisible raw x to fail closed");
        } catch (IllegalStateException expected) {
            assertEquals(true, expected.getMessage().contains("raw x"));
        }
    }

    @Test
    public void rejectsCoordinateOverflowInsteadOfSilentlyWrapping() {
        try {
            MultiblockedScissorTransform.map(
                    Integer.MAX_VALUE, 0, 0, 0,
                    1, 240,
                    2, 92,
                    0, 0
            );
            fail("Expected mapped x overflow to fail closed");
        } catch (IllegalStateException expected) {
            assertEquals(true, expected.getMessage().contains("mapped x"));
        }
    }

    private static void assertBox(MultiblockedScissorTransform.Box actual,
                                  int x, int y, int width, int height) {
        assertEquals(x, actual.x);
        assertEquals(y, actual.y);
        assertEquals(width, actual.width);
        assertEquals(height, actual.height);
    }
}
