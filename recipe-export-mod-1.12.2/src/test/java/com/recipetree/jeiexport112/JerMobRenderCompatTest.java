package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class JerMobRenderCompatTest {
    @Test
    public void mobClipTracksTheScaledRecipeCard() {
        assertArrayEquals(new int[]{214, 1356, 118, 158},
                JerMobRenderCompat.correctedScissor(100, 200, 1F, 2, 2000));
        assertArrayEquals(new int[]{207, 1678, 59, 79},
                JerMobRenderCompat.correctedScissor(100, 100, 0.5F, 2, 2000));
    }
}
