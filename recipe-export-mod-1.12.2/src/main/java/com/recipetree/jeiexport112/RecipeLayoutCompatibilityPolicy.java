package com.recipetree.jeiexport112;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure validation policy for the three known HEI 4.x category-contract violations in
 * MeatballCraft. Keeping identity and shape decisions free of Minecraft/HEI classes makes the
 * fail-closed contract straightforward to unit test.
 */
final class RecipeLayoutCompatibilityPolicy {
    static final String ADVANCED_ROCKETRY_UID = "zmaster587.AR.chemicalReactor";
    static final String ADVANCED_ROCKETRY_CATEGORY =
            "zmaster587.advancedRocketry.integration.jei.chemicalReactor.ChemicalReactorCategory";
    static final String ADVANCED_ROCKETRY_WRAPPER =
            "zmaster587.advancedRocketry.integration.jei.chemicalReactor.ChemicalReactorlWrapper";

    static final String BUILDCRAFT_HEATABLE_UID = "buildcraft:category_heatable";
    static final String BUILDCRAFT_HEATABLE_CATEGORY =
            "buildcraft.compat.module.jei.factory.CategoryHeatable";
    static final String BUILDCRAFT_HEATABLE_WRAPPER =
            "buildcraft.compat.module.jei.factory.WrapperHeatable";

    static final String BUILDCRAFT_COOLABLE_UID = "buildcraft:category_coolable";
    static final String BUILDCRAFT_COOLABLE_CATEGORY =
            "buildcraft.compat.module.jei.factory.CategoryCoolable";
    static final String BUILDCRAFT_COOLABLE_WRAPPER =
            "buildcraft.compat.module.jei.factory.WrapperCoolable";

    private static final String PIPE_SEALER = "advancedrocketry:pipesealer";
    private static final String PRODUCT_SHEET = "advancedrocketry:productsheet";
    private static final String PRESSURE_TANK = "advancedrocketry:pressuretank";

    private static final Set<String> AETHER_SEALABLE_ARMOR = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    "aether_legacy:zanite_helmet",
                    "aether_legacy:zanite_chestplate",
                    "aether_legacy:zanite_leggings",
                    "aether_legacy:zanite_boots",
                    "aether_legacy:gravitite_helmet",
                    "aether_legacy:gravitite_chestplate",
                    "aether_legacy:gravitite_leggings",
                    "aether_legacy:gravitite_boots",
                    "aether_legacy:neptune_helmet",
                    "aether_legacy:neptune_chestplate",
                    "aether_legacy:neptune_leggings",
                    "aether_legacy:neptune_boots",
                    "aether_legacy:phoenix_helmet",
                    "aether_legacy:phoenix_chestplate",
                    "aether_legacy:phoenix_leggings",
                    "aether_legacy:phoenix_boots",
                    "aether_legacy:obsidian_helmet",
                    "aether_legacy:obsidian_chestplate",
                    "aether_legacy:obsidian_leggings",
                    "aether_legacy:obsidian_boots",
                    "aether_legacy:valkyrie_helmet",
                    "aether_legacy:valkyrie_chestplate",
                    "aether_legacy:valkyrie_leggings",
                    "aether_legacy:valkyrie_boots",
                    "aether_legacy:sentry_boots"
            )));

    private RecipeLayoutCompatibilityPolicy() {
    }

    enum Kind {
        NONE("none"),
        ADVANCED_ROCKETRY_EMPTY_WILDCARD_INPUT(
                "advancedRocketryChemicalReactorEmptyWildcardInput"),
        BUILDCRAFT_HEATABLE_ABSENT_OUTPUT("buildCraftHeatableAbsentOutput"),
        BUILDCRAFT_COOLABLE_ABSENT_OUTPUT("buildCraftCoolableAbsentOutput");

        final String diagnosticName;

        Kind(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }
    }

    static Kind classify(String uid, String categoryClass, String wrapperClass) {
        if (matches(uid, categoryClass, wrapperClass,
                ADVANCED_ROCKETRY_UID, ADVANCED_ROCKETRY_CATEGORY, ADVANCED_ROCKETRY_WRAPPER)) {
            return Kind.ADVANCED_ROCKETRY_EMPTY_WILDCARD_INPUT;
        }
        if (matches(uid, categoryClass, wrapperClass,
                BUILDCRAFT_HEATABLE_UID, BUILDCRAFT_HEATABLE_CATEGORY,
                BUILDCRAFT_HEATABLE_WRAPPER)) {
            return Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT;
        }
        if (matches(uid, categoryClass, wrapperClass,
                BUILDCRAFT_COOLABLE_UID, BUILDCRAFT_COOLABLE_CATEGORY,
                BUILDCRAFT_COOLABLE_WRAPPER)) {
            return Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT;
        }

        if (isKnownUid(uid) || isKnownCategory(categoryClass) || isKnownWrapper(wrapperClass)) {
            throw violation("known category identity drift: uid=" + quoted(uid) +
                    ", categoryClass=" + quoted(categoryClass) +
                    ", wrapperClass=" + quoted(wrapperClass));
        }
        return Kind.NONE;
    }

    static boolean requiresAdvancedRocketryPatch(List<List<StackRef>> itemInputs,
                                                  List<List<StackRef>> itemOutputs,
                                                  int fluidInputSlots,
                                                  int fluidOutputSlots) {
        requireNonNull(itemInputs, "Advanced Rocketry item inputs");
        requireNonNull(itemOutputs, "Advanced Rocketry item outputs");
        requireNonNegative(fluidInputSlots, "Advanced Rocketry fluid input slot count");
        requireNonNegative(fluidOutputSlots, "Advanced Rocketry fluid output slot count");

        int emptyInputSlot = firstEmptyOrNullSlot(itemInputs);
        if (emptyInputSlot < 0) {
            return false;
        }

        require(emptyInputSlot == 0,
                "Advanced Rocketry empty wildcard input moved from slot 0 to slot " +
                        emptyInputSlot);
        require(itemInputs.get(0) != null,
                "Advanced Rocketry wildcard input slot 0 changed from empty to null");
        require(countEmptyOrNullSlots(itemInputs) == 1,
                "Advanced Rocketry expected exactly one empty item-input slot");
        require(fluidInputSlots == 0 && fluidOutputSlots == 0,
                "Advanced Rocketry compatibility target unexpectedly contains fluid slots");
        require(itemOutputs.size() == 1,
                "Advanced Rocketry compatibility target must have one item-output slot");

        StackRef output = requireSingletonStack(itemOutputs.get(0),
                "Advanced Rocketry item output slot 0");
        require(AETHER_SEALABLE_ARMOR.contains(output.id),
                "Advanced Rocketry empty wildcard input has unrecognized output " +
                        quoted(output.id));
        require(output.count == 1,
                "Advanced Rocketry armor output count changed from 1 to " + output.count);

        boolean chestplate = output.id.endsWith("_chestplate");
        int expectedInputSlots = chestplate ? 4 : 3;
        require(itemInputs.size() == expectedInputSlots,
                "Advanced Rocketry " + output.id + " expected " + expectedInputSlots +
                        " item-input slots but found " + itemInputs.size());
        requireStack(itemInputs.get(1), PIPE_SEALER, 1,
                "Advanced Rocketry pipe-sealer input slot 1");
        requireStack(itemInputs.get(2), PRODUCT_SHEET, 4,
                "Advanced Rocketry product-sheet input slot 2");
        if (chestplate) {
            requireStack(itemInputs.get(3), PRESSURE_TANK, 1,
                    "Advanced Rocketry pressure-tank input slot 3");
        }
        return true;
    }

    static boolean requiresBuildCraftPatch(Kind kind,
                                            int itemInputSlots,
                                            int itemOutputSlots,
                                            List<List<FluidRef>> fluidInputs,
                                            List<List<FluidRef>> fluidOutputs) {
        require(kind == Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT ||
                        kind == Kind.BUILDCRAFT_COOLABLE_ABSENT_OUTPUT,
                "BuildCraft shape validation received incompatible mode " + kind);
        requireNonNegative(itemInputSlots, "BuildCraft item input slot count");
        requireNonNegative(itemOutputSlots, "BuildCraft item output slot count");
        requireNonNull(fluidInputs, "BuildCraft fluid inputs");
        requireNonNull(fluidOutputs, "BuildCraft fluid outputs");

        if (!fluidOutputs.isEmpty()) {
            require(firstEmptyOrNullSlot(fluidOutputs) < 0,
                    "BuildCraft non-empty fluid outputs contain an empty or null slot");
            return false;
        }

        require(itemInputSlots == 0 && itemOutputSlots == 0,
                "BuildCraft absent-output compatibility target unexpectedly contains item slots");
        require(fluidInputs.size() == 1,
                "BuildCraft absent-output compatibility target must have one fluid-input slot");
        FluidRef input = requireSingletonFluid(fluidInputs.get(0),
                "BuildCraft fluid input slot 0");
        String expectedFluid = kind == Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT ? "water" : "lava";
        int expectedAmount = kind == Kind.BUILDCRAFT_HEATABLE_ABSENT_OUTPUT ? 10 : 5;
        require(expectedFluid.equals(input.id) && input.amount == expectedAmount,
                "BuildCraft " + kind.diagnosticName + " expected " + expectedAmount + " mB of " +
                        expectedFluid + " but found " + input.amount + " mB of " + quoted(input.id));
        return true;
    }

    static <T> List<List<T>> replaceFirstEmptySlot(List<List<T>> source, T sentinel) {
        requireNonNull(source, "replacement source");
        require(!source.isEmpty() && source.get(0) != null && source.get(0).isEmpty(),
                "replacement requires an empty, non-null slot 0");
        require(sentinel != null, "replacement sentinel must not be null");
        List<List<T>> copy = immutableDeepCopy(source);
        List<List<T>> replaced = new ArrayList<List<T>>(copy);
        replaced.set(0, Collections.singletonList(sentinel));
        return Collections.unmodifiableList(replaced);
    }

    static <T> List<List<T>> singletonEmptySlot() {
        return Collections.singletonList(Collections.<T>emptyList());
    }

    private static boolean matches(String uid, String categoryClass, String wrapperClass,
                                   String expectedUid, String expectedCategory,
                                   String expectedWrapper) {
        return expectedUid.equals(uid) && expectedCategory.equals(categoryClass) &&
                expectedWrapper.equals(wrapperClass);
    }

    private static boolean isKnownUid(String uid) {
        return ADVANCED_ROCKETRY_UID.equals(uid) || BUILDCRAFT_HEATABLE_UID.equals(uid) ||
                BUILDCRAFT_COOLABLE_UID.equals(uid);
    }

    private static boolean isKnownCategory(String className) {
        return ADVANCED_ROCKETRY_CATEGORY.equals(className) ||
                BUILDCRAFT_HEATABLE_CATEGORY.equals(className) ||
                BUILDCRAFT_COOLABLE_CATEGORY.equals(className);
    }

    private static boolean isKnownWrapper(String className) {
        return ADVANCED_ROCKETRY_WRAPPER.equals(className) ||
                BUILDCRAFT_HEATABLE_WRAPPER.equals(className) ||
                BUILDCRAFT_COOLABLE_WRAPPER.equals(className);
    }

    private static <T> int firstEmptyOrNullSlot(List<List<T>> slots) {
        for (int i = 0; i < slots.size(); i++) {
            List<T> slot = slots.get(i);
            if (slot == null || slot.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static <T> int countEmptyOrNullSlots(List<List<T>> slots) {
        int count = 0;
        for (List<T> slot : slots) {
            if (slot == null || slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static StackRef requireSingletonStack(List<StackRef> slot, String label) {
        require(slot != null && slot.size() == 1,
                label + " must contain exactly one stack");
        StackRef stack = slot.get(0);
        require(stack != null && stack.id != null && !stack.id.isEmpty() && stack.count > 0,
                label + " contains an invalid stack descriptor");
        return stack;
    }

    private static FluidRef requireSingletonFluid(List<FluidRef> slot, String label) {
        require(slot != null && slot.size() == 1,
                label + " must contain exactly one fluid stack");
        FluidRef fluid = slot.get(0);
        require(fluid != null && fluid.id != null && !fluid.id.isEmpty() && fluid.amount > 0,
                label + " contains an invalid fluid descriptor");
        return fluid;
    }

    private static void requireStack(List<StackRef> slot, String expectedId, int expectedCount,
                                     String label) {
        StackRef stack = requireSingletonStack(slot, label);
        require(expectedId.equals(stack.id) && stack.count == expectedCount,
                label + " expected " + expectedCount + "x " + expectedId + " but found " +
                        stack.count + "x " + quoted(stack.id));
    }

    private static <T> List<List<T>> immutableDeepCopy(List<List<T>> source) {
        List<List<T>> copy = new ArrayList<List<T>>(source.size());
        for (List<T> slot : source) {
            require(slot != null, "replacement source contains a null slot");
            copy.add(Collections.unmodifiableList(new ArrayList<T>(slot)));
        }
        return Collections.unmodifiableList(copy);
    }

    private static void requireNonNull(Object value, String label) {
        require(value != null, label + " must not be null");
    }

    private static void requireNonNegative(int value, String label) {
        require(value >= 0, label + " must not be negative");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw violation(message);
        }
    }

    private static IllegalStateException violation(String message) {
        return new IllegalStateException("RECIPE_LAYOUT_COMPAT_DRIFT: " + message);
    }

    private static String quoted(String value) {
        return value == null ? "<null>" : "'" + value + "'";
    }

    static final class StackRef {
        final String id;
        final int count;

        StackRef(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    static final class FluidRef {
        final String id;
        final int amount;

        FluidRef(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }
    }
}
