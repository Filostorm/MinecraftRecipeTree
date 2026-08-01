package com.recipetree.jeiexport;

/** Overflow-safe quantity propagation for the in-game recipe tree. */
final class RecipeQuantityMath {
    private RecipeQuantityMath() {
    }

    static long craftsFor(long requestedOutput, long outputPerCraft) {
        long requested = Math.max(1, requestedOutput);
        long yield = Math.max(1, outputPerCraft);
        return 1 + (requested - 1) / yield;
    }

    static long inputTotal(long inputPerCraft, long crafts) {
        long input = Math.max(1, inputPerCraft);
        long craftCount = Math.max(1, crafts);
        if (input > Long.MAX_VALUE / craftCount) return Long.MAX_VALUE;
        return input * craftCount;
    }

    static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
