package com.recipetree.neiexport1710;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MobsInfoVillagerTradeSemanticAdapterTest {
    @Test
    public void clampsOnlyUpperBoundaryRoundoff() {
        assertEquals(1.0d,
                MobsInfoVillagerTradeSemanticAdapter.normalizeProbability(
                        1.0000000000000007d), 0.0d);
        assertEquals(1.0d + 2.0e-12d,
                MobsInfoVillagerTradeSemanticAdapter.normalizeProbability(
                        1.0d + 2.0e-12d), 0.0d);
    }

    @Test
    public void leavesOrdinaryAndInvalidValuesForFailClosedValidation() {
        assertEquals(0.25d,
                MobsInfoVillagerTradeSemanticAdapter.normalizeProbability(0.25d), 0.0d);
        assertEquals(0.0d,
                MobsInfoVillagerTradeSemanticAdapter.normalizeProbability(0.0d), 0.0d);
        assertEquals(Double.POSITIVE_INFINITY,
                MobsInfoVillagerTradeSemanticAdapter.normalizeProbability(
                        Double.POSITIVE_INFINITY), 0.0d);
    }
}
