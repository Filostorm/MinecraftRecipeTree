package com.recipetree.jeiexport112.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure, deterministic selection policy used by the launch-time Forge adapter. */
final class WorldStartupDimensionPolicy {
    static final String POLICY_NAME = "dimension-0-plus-should-load-spawn";

    interface LoadSpawnLookup {
        boolean shouldLoadSpawn(int dimensionId);
    }

    private WorldStartupDimensionPolicy() {
    }

    static Selection select(Integer[] original, LoadSpawnLookup lookup) {
        if (original == null) {
            throw invalid("DimensionManager.getStaticDimensionIDs() returned null");
        }
        if (lookup == null) {
            throw invalid("the DimensionType lookup is null");
        }

        Set<Integer> seen = new LinkedHashSet<Integer>();
        List<Integer> selected = new ArrayList<Integer>(original.length);
        List<Integer> skipped = new ArrayList<Integer>(original.length);
        boolean containsOverworld = false;

        for (int index = 0; index < original.length; index++) {
            Integer boxedId = original[index];
            if (boxedId == null) {
                throw invalid("static dimension ID at index " + index + " is null");
            }
            if (!seen.add(boxedId)) {
                throw invalid("static dimension ID " + boxedId + " occurs more than once");
            }

            int dimensionId = boxedId.intValue();
            boolean shouldLoadSpawn = lookup.shouldLoadSpawn(dimensionId);
            if (dimensionId == 0) {
                containsOverworld = true;
            }
            if (dimensionId == 0 || shouldLoadSpawn) {
                selected.add(boxedId);
            } else {
                skipped.add(boxedId);
            }
        }

        if (!containsOverworld) {
            throw invalid("static dimensions do not contain required dimension 0");
        }

        return new Selection(
                original.clone(),
                selected.toArray(new Integer[selected.size()]),
                skipped.toArray(new Integer[skipped.size()])
        );
    }

    private static IllegalStateException invalid(String detail) {
        return new IllegalStateException(
                "[jeiexport] Invalid Forge startup-dimension registry: " + detail +
                        ". Refusing to fall back to loading every registered dimension."
        );
    }

    static final class Selection {
        private final Integer[] original;
        private final Integer[] selected;
        private final Integer[] skipped;

        private Selection(Integer[] original, Integer[] selected, Integer[] skipped) {
            this.original = original;
            this.selected = selected;
            this.skipped = skipped;
        }

        Integer[] selectedCopy() {
            return selected.clone();
        }

        int originalCount() {
            return original.length;
        }

        int selectedCount() {
            return selected.length;
        }

        int skippedCount() {
            return skipped.length;
        }

        String originalIds() {
            return Arrays.toString(original);
        }

        String selectedIds() {
            return Arrays.toString(selected);
        }

        String skippedIds() {
            return Arrays.toString(skipped);
        }
    }
}
