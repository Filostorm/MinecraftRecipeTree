package com.recipetree.jeiexport112.compat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class WorldStartupDimensionPolicyTest {
    @Test
    public void selectsOverworldAndLoadSpawnTypesInOriginalOrder() {
        final Set<Integer> loadSpawn = new HashSet<Integer>(Arrays.asList(-7, 42));
        final int[] lookups = {0};

        WorldStartupDimensionPolicy.Selection selection = WorldStartupDimensionPolicy.select(
                new Integer[]{-1, 0, -7, 42, 1, 99},
                new WorldStartupDimensionPolicy.LoadSpawnLookup() {
                    @Override
                    public boolean shouldLoadSpawn(int dimensionId) {
                        lookups[0]++;
                        return loadSpawn.contains(dimensionId);
                    }
                }
        );

        assertArrayEquals(new Integer[]{0, -7, 42}, selection.selectedCopy());
        assertEquals(6, selection.originalCount());
        assertEquals(3, selection.selectedCount());
        assertEquals(3, selection.skippedCount());
        assertEquals("every registered ID is validated exactly once", 6, lookups[0]);
        assertEquals("[-1, 0, -7, 42, 1, 99]", selection.originalIds());
        assertEquals("[0, -7, 42]", selection.selectedIds());
        assertEquals("[-1, 1, 99]", selection.skippedIds());
    }

    @Test
    public void selectedArrayIsDefensivelyCopied() {
        WorldStartupDimensionPolicy.Selection selection = selectNoneExceptOverworld(
                new Integer[]{0, 1}
        );
        Integer[] first = selection.selectedCopy();
        first[0] = 123;
        assertArrayEquals(new Integer[]{0}, selection.selectedCopy());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNullRegistryArray() {
        WorldStartupDimensionPolicy.select(null, lookupFalse());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNullDimensionId() {
        selectNoneExceptOverworld(new Integer[]{0, null});
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsDuplicateDimensionId() {
        selectNoneExceptOverworld(new Integer[]{0, 12, 12});
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsRegistryWithoutDimensionZero() {
        selectNoneExceptOverworld(new Integer[]{-1, 1});
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsNullDimensionTypeLookup() {
        WorldStartupDimensionPolicy.select(new Integer[]{0}, null);
    }

    private static WorldStartupDimensionPolicy.Selection selectNoneExceptOverworld(Integer[] ids) {
        return WorldStartupDimensionPolicy.select(ids, lookupFalse());
    }

    private static WorldStartupDimensionPolicy.LoadSpawnLookup lookupFalse() {
        return new WorldStartupDimensionPolicy.LoadSpawnLookup() {
            @Override
            public boolean shouldLoadSpawn(int dimensionId) {
                return false;
            }
        };
    }
}
