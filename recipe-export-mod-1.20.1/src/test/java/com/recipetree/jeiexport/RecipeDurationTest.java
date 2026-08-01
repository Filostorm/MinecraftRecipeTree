package com.recipetree.jeiexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeDurationTest {
    @Test
    void readsCommonDurationAccessor() {
        assertEquals(240, RecipeDuration.ticks(new TimedRecipe()).orElseThrow());
    }

    @Test
    void derivesTicksFromJeiEnergyLabels() {
        assertEquals(50, RecipeDuration.ticks(new EnergyRecipe()).orElseThrow());
    }

    @Test
    void rejectsNonIntegralEnergyRatios() {
        assertTrue(RecipeDuration.ticks(new AmbiguousEnergyRecipe()).isEmpty());
    }

    public static final class TimedRecipe {
        public int getProcessingTime() { return 240; }
    }

    public static final class EnergyRecipe {
        public long getTotalEnergy() { return 10_000_000L; }
        public long getEnergyPerTick() { return 200_000L; }
    }

    public static final class AmbiguousEnergyRecipe {
        public long getTotalEnergy() { return 10L; }
        public long getEnergyPerTick() { return 3L; }
    }
}
