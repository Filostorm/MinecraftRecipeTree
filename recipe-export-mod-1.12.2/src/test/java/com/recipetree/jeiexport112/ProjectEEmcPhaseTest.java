package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectEEmcPhaseTest {
    @Test
    public void exportsEveryPositiveSafeValueRegardlessOfOtherRecipes() {
        assertTrue(ProjectEEmcPhase.shouldExport(true, 8_192L));
        assertFalse(ProjectEEmcPhase.shouldExport(false, 8_192L));
        assertFalse(ProjectEEmcPhase.shouldExport(true, 0L));
        assertFalse(ProjectEEmcPhase.shouldExport(true, 9_007_199_254_740_992L));
    }
}
