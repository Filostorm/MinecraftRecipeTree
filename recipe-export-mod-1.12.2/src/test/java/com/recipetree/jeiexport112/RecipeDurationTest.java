package com.recipetree.jeiexport112;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RecipeDurationTest {
    @Test
    public void readsCommonDurationAccessor() {
        assertEquals(240L, RecipeDuration.ticks(new TimedRecipe()).getAsLong());
    }

    @Test
    public void derivesTicksFromJeiEnergyLabels() {
        assertEquals(50L, RecipeDuration.ticks(new EnergyRecipe()).getAsLong());
    }

    @Test
    public void rejectsNonIntegralEnergyRatios() {
        assertFalse(RecipeDuration.ticks(new AmbiguousEnergyRecipe()).isPresent());
    }

    public static final class TimedRecipe {
        public int getProcessingTime() { return 240; }
    }

    public static final class EnergyRecipe {
        public long getTotalEnergy() { return 10000000L; }
        public long getEnergyPerTick() { return 200000L; }
    }

    public static final class AmbiguousEnergyRecipe {
        public long getTotalEnergy() { return 10L; }
        public long getEnergyPerTick() { return 3L; }
    }
}
