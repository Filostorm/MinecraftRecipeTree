package com.recipetree.neiexport1710;

/**
 * Pure, fail-closed shape predicate for a GregTech {@code FuelBackend} row whose
 * semantic result is consumed fuel/energy rather than an item or fluid stack.
 *
 * <p>The runtime preflight supplies exact class and owner-identity observations;
 * this class deliberately contains no class-name or reflective fallback.</p>
 */
final class GregTechFuelInformationalContract {
    static final String CONTRACT = "gregtech-fuel-backend-input-sink-v1";

    static final class Pins {
        final Class<?> handlerClass;
        final Class<?> cachedRecipeClass;
        final Class<?> recipeMapClass;
        final Class<?> fuelBackendClass;

        Pins(Class<?> handlerClass, Class<?> cachedRecipeClass,
             Class<?> recipeMapClass, Class<?> fuelBackendClass) {
            this.handlerClass = required(handlerClass, "handlerClass");
            this.cachedRecipeClass = required(cachedRecipeClass, "cachedRecipeClass");
            this.recipeMapClass = required(recipeMapClass, "recipeMapClass");
            this.fuelBackendClass = required(fuelBackendClass, "fuelBackendClass");
        }

        private static Class<?> required(Class<?> value, String label) {
            if (value == null) {
                throw new IllegalArgumentException(label + " is required");
            }
            return value;
        }
    }

    static final class Observation {
        final Object handler;
        final Object cachedRecipe;
        final boolean sourceIndexIdentityVerified;
        final Object recipeMap;
        final Object handlerRecipeMap;
        final Object backend;
        final Object mapBackend;
        final String overlay;
        final String unlocalizedName;
        final boolean registerNei;
        final int rawItemInputs;
        final int rawFluidInputs;
        final int neiIngredients;
        final int rawItemOutputs;
        final int rawFluidOutputs;
        final boolean resultPresent;
        final int neiOtherStacks;
        final int specialValue;
        final boolean specialItemPresent;
        final boolean enabled;
        final boolean hidden;

        Observation(Object handler, Object cachedRecipe,
                    boolean sourceIndexIdentityVerified,
                    Object recipeMap, Object handlerRecipeMap,
                    Object backend, Object mapBackend,
                    String overlay, String unlocalizedName,
                    boolean registerNei,
                    int rawItemInputs, int rawFluidInputs, int neiIngredients,
                    int rawItemOutputs, int rawFluidOutputs,
                    boolean resultPresent, int neiOtherStacks,
                    int specialValue, boolean specialItemPresent,
                    boolean enabled, boolean hidden) {
            this.handler = handler;
            this.cachedRecipe = cachedRecipe;
            this.sourceIndexIdentityVerified = sourceIndexIdentityVerified;
            this.recipeMap = recipeMap;
            this.handlerRecipeMap = handlerRecipeMap;
            this.backend = backend;
            this.mapBackend = mapBackend;
            this.overlay = overlay;
            this.unlocalizedName = unlocalizedName;
            this.registerNei = registerNei;
            this.rawItemInputs = rawItemInputs;
            this.rawFluidInputs = rawFluidInputs;
            this.neiIngredients = neiIngredients;
            this.rawItemOutputs = rawItemOutputs;
            this.rawFluidOutputs = rawFluidOutputs;
            this.resultPresent = resultPresent;
            this.neiOtherStacks = neiOtherStacks;
            this.specialValue = specialValue;
            this.specialItemPresent = specialItemPresent;
            this.enabled = enabled;
            this.hidden = hidden;
        }
    }

    /**
     * Exact shape emitted by {@code LargeBoilerFuelBackend.addSolidRecipe}.
     *
     * <p>Unlike ordinary {@code FuelBackend} rows, the backend stores the integer quotient of the
     * furnace fuel value divided by 1600 in {@code mSpecialValue}. Valid fuels in the inclusive
     * 400..1599 range therefore carry a zero special value; their nonzero burn-time semantics are
     * retained in the backend-generated five-line NEI description.</p>
     */
    static final class LargeBoilerSolidObservation {
        final int rawItemInputs;
        final int rawFluidInputs;
        final int neiIngredients;
        final int rawItemOutputs;
        final int rawFluidOutputs;
        final boolean resultPresent;
        final int neiOtherStacks;
        final int duration;
        final int eut;
        final int specialValue;
        final int resolvedFurnaceFuelValue;
        final boolean specialItemPresent;
        final boolean fakeRecipe;
        final boolean enabled;
        final boolean hidden;
        final int neiDescriptionLines;
        final boolean allNeiDescriptionLinesNonblank;

        LargeBoilerSolidObservation(
                int rawItemInputs, int rawFluidInputs, int neiIngredients,
                int rawItemOutputs, int rawFluidOutputs,
                boolean resultPresent, int neiOtherStacks,
                int duration, int eut, int specialValue, int resolvedFurnaceFuelValue,
                boolean specialItemPresent, boolean fakeRecipe,
                boolean enabled, boolean hidden,
                int neiDescriptionLines, boolean allNeiDescriptionLinesNonblank) {
            this.rawItemInputs = rawItemInputs;
            this.rawFluidInputs = rawFluidInputs;
            this.neiIngredients = neiIngredients;
            this.rawItemOutputs = rawItemOutputs;
            this.rawFluidOutputs = rawFluidOutputs;
            this.resultPresent = resultPresent;
            this.neiOtherStacks = neiOtherStacks;
            this.duration = duration;
            this.eut = eut;
            this.specialValue = specialValue;
            this.resolvedFurnaceFuelValue = resolvedFurnaceFuelValue;
            this.specialItemPresent = specialItemPresent;
            this.fakeRecipe = fakeRecipe;
            this.enabled = enabled;
            this.hidden = hidden;
            this.neiDescriptionLines = neiDescriptionLines;
            this.allNeiDescriptionLinesNonblank = allNeiDescriptionLinesNonblank;
        }
    }

    private GregTechFuelInformationalContract() {
    }

    static boolean isCanonicalFuelRowBinding(Pins pins, Observation observed) {
        if (pins == null || observed == null
                || observed.handler == null || observed.cachedRecipe == null
                || observed.recipeMap == null || observed.backend == null) {
            return false;
        }
        if (observed.handler.getClass() != pins.handlerClass
                || observed.cachedRecipe.getClass() != pins.cachedRecipeClass
                || observed.recipeMap.getClass() != pins.recipeMapClass
                || observed.backend.getClass() != pins.fuelBackendClass) {
            return false;
        }
        if (!observed.sourceIndexIdentityVerified
                || observed.handlerRecipeMap != observed.recipeMap
                || observed.mapBackend != observed.backend) {
            return false;
        }
        if (observed.overlay == null
                || !observed.overlay.equals(observed.unlocalizedName)
                || !observed.registerNei) {
            return false;
        }
        if (observed.rawItemInputs < 0 || observed.rawFluidInputs < 0
                || observed.neiIngredients < 0 || observed.rawItemOutputs < 0
                || observed.rawFluidOutputs < 0 || observed.neiOtherStacks < 0) {
            return false;
        }
        if (observed.rawItemInputs + observed.rawFluidInputs <= 0
                || observed.neiIngredients <= 0) {
            return false;
        }
        if (observed.rawItemOutputs != 0 || observed.rawFluidOutputs != 0
                || observed.resultPresent || observed.neiOtherStacks != 0) {
            return false;
        }
        return observed.specialValue > 0
                && !observed.specialItemPresent
                && observed.enabled
                && !observed.hidden;
    }

    static boolean isCanonicalLargeBoilerSolidFuelRow(
            LargeBoilerSolidObservation observed) {
        if (observed == null) {
            return false;
        }
        if (observed.rawItemInputs != 1 || observed.rawFluidInputs != 0
                || observed.neiIngredients != 1) {
            return false;
        }
        if (observed.rawItemOutputs != 0 || observed.rawFluidOutputs != 0
                || observed.resultPresent || observed.neiOtherStacks != 0) {
            return false;
        }
        if (observed.duration != 1 || observed.eut != 0
                || observed.resolvedFurnaceFuelValue < 400
                || observed.specialValue != observed.resolvedFurnaceFuelValue / 1600) {
            return false;
        }
        return !observed.specialItemPresent
                && !observed.fakeRecipe
                && observed.enabled
                && !observed.hidden
                && observed.neiDescriptionLines == 5
                && observed.allNeiDescriptionLinesNonblank;
    }
}
