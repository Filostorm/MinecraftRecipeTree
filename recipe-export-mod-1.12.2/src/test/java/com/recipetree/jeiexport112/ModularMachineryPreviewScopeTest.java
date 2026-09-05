package com.recipetree.jeiexport112;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class ModularMachineryPreviewScopeTest {
    @Test
    public void movesNativeSceneIntoItsRecipeCard() {
        assertArrayEquals(new int[]{808, 588, 400, 360},
                ModularMachineryPreviewScope.mapViewport(8, 948, 400, 360,
                        800, 360, 1F, 1440));
    }

    @Test
    public void scalesPositionAndSizeTogetherWhenZoomedOut() {
        assertArrayEquals(new int[]{804, 924, 200, 180},
                ModularMachineryPreviewScope.mapViewport(8, 948, 400, 360,
                        800, 270, 0.5F, 1440));
    }

    @Test
    public void preservesOffscreenOriginInsteadOfShiftingClippedPreview() {
        assertArrayEquals(new int[]{-84, 556, 800, 720},
                ModularMachineryPreviewScope.mapViewport(8, 948, 400, 360,
                        -100, -100, 2F, 1440));
    }
}
